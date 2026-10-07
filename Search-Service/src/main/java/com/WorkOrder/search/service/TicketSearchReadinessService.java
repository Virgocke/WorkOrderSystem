package com.WorkOrder.search.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.search.config.TicketSearchSyncProperties;
import com.WorkOrder.search.mapper.TicketSearchTaskMapper;
import com.WorkOrder.search.model.TicketSearchControlState;
import com.WorkOrder.search.model.TicketSearchImportTask;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 每次查询检查主库就绪状态及真实读别名；重建期间不返回旧索引搜索结果。
 */
@Service
public class TicketSearchReadinessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketSearchReadinessService.class);

    /**
     * 即使同步关闭也存在，用于明确返回未就绪而非删除 HTTP 入口。
     */
    private final TicketSearchSyncProperties properties;
    /**
     * 可选依赖保证关闭 ES 或同步时仍能启动搜索控制器。
     */
    private final ObjectProvider<TicketSearchTaskMapper> mapperProvider;
    private final ObjectProvider<TicketSearchIndexService> indexServiceProvider;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    /**
     * 保存可选基础设施依赖，构造时不查询数据库或 Elasticsearch。
     *
     * @param properties 即使同步关闭也存在，用于明确返回未就绪而非删除 HTTP 入口
     * @param mapperProvider 可选依赖保证关闭 ES 或同步时仍能启动搜索控制器
     * @param indexServiceProvider 可选 ES 服务，用于重新校验真实已发布读别名
     * @param transactionManagerProvider 可选主库事务管理器，用于隔离外层事务的旧快照
     */
    public TicketSearchReadinessService(TicketSearchSyncProperties properties,
                                       ObjectProvider<TicketSearchTaskMapper> mapperProvider,
                                       ObjectProvider<TicketSearchIndexService> indexServiceProvider,
                                       ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this.properties = properties;
        this.mapperProvider = mapperProvider;
        this.indexServiceProvider = indexServiceProvider;
        this.transactionManagerProvider = transactionManagerProvider;
    }

    /**
     * 在新的短事务中读取已发布任务，结束事务后回读真实 ES 别名。
     *
     * @return 本次搜索开始时已确认发布的不可变目标
     * @throws SystemException 同步关闭、数据库状态未就绪、标记不一致或别名校验失败
     */
    public TicketSearchWriteTarget requireReady() {
        if (!properties.isEnabled()) {
            throw notReady();
        }
        TicketSearchTaskMapper mapper = mapperProvider.getIfAvailable();
        TicketSearchIndexService indexService = indexServiceProvider.getIfAvailable();
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (mapper == null || indexService == null || transactionManager == null) {
            throw notReady();
        }
        try {
            // 先挂起调用方事务，避免它的旧快照影响就绪判断，也避免 ES 请求占用该事务。
            TransactionTemplate detached = new TransactionTemplate(transactionManager);
            detached.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            return detached.execute(status -> checkOutsideTransaction(mapper, indexService, transactionManager));
        } catch (SystemException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("工单搜索就绪校验失败，failureType={}", exception.getClass().getSimpleName());
            SystemException unavailable = notReady();
            unavailable.initCause(exception);
            throw unavailable;
        }
    }

    /**
     * 暂停外层事务，短读结束后再发 ES 请求，避免恢复的外层事务包住网络调用。
     *
     * @param mapper 工单搜索任务数据访问器
     * @param indexService 工单搜索索引服务
     * @param transactionManager 主库事务管理器，用于暂停外层事务并执行独立短读
     * @return 共享 READY 事实及真实 ES 读别名均通过校验的不可变目标
     */
    private TicketSearchWriteTarget checkOutsideTransaction(TicketSearchTaskMapper mapper,
                                                            TicketSearchIndexService indexService,
                                                            PlatformTransactionManager transactionManager) {
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        // 每次门禁都重新读取主库；沿用查询方事务可能看不到刚开始的重建或刚完成的发布。
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
        TicketSearchWriteTarget target = read.execute(status -> readReadyTarget(mapper));
        try {
            // 短读事务已经结束，再确认 ES 别名实际指向该代次；仅有数据库 READY 标记还不够。
            indexService.validatePublishedReadAlias(target);
            return target;
        } catch (IOException exception) {
            LOGGER.warn("工单搜索读别名校验失败，failureType={}", exception.getClass().getSimpleName());
            SystemException unavailable = notReady();
            unavailable.initCause(exception);
            throw unavailable;
        }
    }

    /**
     * 搜索结束后重新检查控制状态及读别名，变更代次时丢弃此次结果。
     *
     * @param expected 查询开始时固定的已发布目标
     * @throws SystemException 搜索期间关闭、开始重建或发布目标发生变化
     */
    public void assertStillReady(TicketSearchWriteTarget expected) {
        if (expected == null) {
            throw notReady();
        }
        // 查询开始后仍可能发生重建或切换，结束时发现代次变化就丢弃这次结果。
        TicketSearchWriteTarget current = requireReady();
        if (current.getJobId() != expected.getJobId() || current.getGeneration() != expected.getGeneration()
                || !current.getPhysicalIndex().equals(expected.getPhysicalIndex())
                || !current.getWriteAlias().equals(expected.getWriteAlias())) {
            throw notReady();
        }
    }

    /**
     * 仅在数据库短事务中执行，不访问 ES，不推进任务状态。
     *
     * @param mapper 工单搜索任务数据访问器
     * @return 由当前共享 READY 控制记录和已发布任务解析的不可变目标
     */
    private TicketSearchWriteTarget readReadyTarget(TicketSearchTaskMapper mapper) {
        TicketSearchControlState state = mapper.selectState();
        if (state == null || !"READY".equals(state.getPhase()) || !positive(state.getCurrentJobId())) {
            throw notReady();
        }
        return validatedPublishedTarget(state, mapper.selectTask(state.getCurrentJobId()));
    }

    /**
     * 校验主库已发布目标的完整事实，供关键词查询及重复发布共用同一规则。
     * 此方法不访问数据库或 ES，调用方仍须新鲜读主库并校验真实读别名。
     *
     * @param state 本次读取的唯一控制记录
     * @param task 本次读取的当前任务
     * @return 全部状态、完成标记与部署确认一致的不可变目标
     * @throws SystemException 任一已发布事实缺失或不一致
     */
    public static TicketSearchWriteTarget validatedPublishedTarget(TicketSearchControlState state,
                                                                  TicketSearchImportTask task) {
        // READY 必须同时指向同一写入代次和发布代次，不能只凭一个阶段字符串开放搜索。
        if (state == null || !Long.valueOf(1).equals(state.getId()) || !"READY".equals(state.getPhase())
                || !positive(state.getCurrentJobId()) || !positive(state.getWriteGeneration())
                || !state.getWriteGeneration().equals(state.getPublishedGeneration())
                || StringUtils.hasText(state.getLastError())) {
            throw notReady();
        }
        // 完整核对任务完成顺序及部署确认，防止缺少验证、刷新或来源确认的任务被误判为已发布。
        if (task == null || !state.getCurrentJobId().equals(task.getId())
                || !state.getWriteGeneration().equals(task.getGeneration()) || !"READY".equals(task.getStatus())
                || task.getTargetInitializedAt() == null
                || task.getVerifiedAt() == null || task.getRefreshCompletedAt() == null
                || task.getVerifiedAt().isBefore(task.getTargetInitializedAt())
                || task.getRefreshCompletedAt().isBefore(task.getVerifiedAt())
                || !positive(task.getDeploymentConfirmedBy()) || task.getDeploymentConfirmedAt() == null
                || !StringUtils.hasText(task.getDeploymentManifest())
                || StringUtils.hasText(task.getLastError())) {
            throw notReady();
        }
        String expectedIndex = "wo-ticket-v3-" + task.getId();
        String expectedAlias = "wo-ticket-write-" + task.getId();
        if (!expectedIndex.equals(task.getTargetIndex()) || !expectedAlias.equals(task.getWriteAlias())
                || !expectedAlias.equals(state.getWriteAlias())) {
            throw notReady();
        }
        return task.target();
    }

    /**
     * 判断任务标识和代次是否为正数。
     *
     * @param value 待检查的可空任务 ID 或重建代次
     * @return 值非 null 且大于 0 时为 true
     */
    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    /**
     * 未就绪统一使用可跨服务识别的业务错误。
     *
     * @return 错误码为 TICKET_SEARCH_NOT_READY 的业务异常
     */
    private static SystemException notReady() {
        return new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY);
    }
}
