package com.WorkOrder.search.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.search.config.TicketSearchSyncProperties;
import com.WorkOrder.search.mapper.TicketIndexSourceMapper;
import com.WorkOrder.search.model.*;
import com.WorkOrder.search.model.admin.*;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.validation.Validator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理入口负责身份、参数和开关，工作者只操作已登记任务。
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class TicketSearchImportService {
    private final TicketSearchTaskStore store;
    private final TicketSearchSyncProperties properties;
    private final TicketSearchReadinessService readiness;
    private final TicketSearchRuntimeStatusService runtime;
    private final ObjectProvider<TicketSearchPublishService> publishers;
    private final ObjectProvider<TicketProjectionService> projections;
    private final TicketIndexSourceMapper sources;
    private final CurrentUserIdProvider users;
    private final Validator validator;
    private final ObjectMapper json;

    /**
     * 网络组件采用可选依赖，使关闭 ES 后管理入口仍可说明未就绪状态。
     *
     * @param store 以短事务维护共享任务、租约和代次的存储服务
     * @param properties 工单搜索同步配置属性
     * @param readiness 校验共享 READY 事实及实际读别名的服务
     * @param runtime 独立观测 Outbox 积压和现有 RocketMQ 消费者的服务
     * @param publishers 可选发布服务，负责固定任务的原子读别名发布及恢复
     * @param projections 可选投影服务，负责按主库源版本修复单条工单
     * @param sources 读取完整工单源行和同次源版本的 Mapper
     * @param users 从已认证上下文读取当前管理员 ID 的提供器
     * @param validator 校验器
     * @param json 序列化源服务部署确认清单的 JSON 组件
     */
    public TicketSearchImportService(TicketSearchTaskStore store, TicketSearchSyncProperties properties,
                                     TicketSearchReadinessService readiness, TicketSearchRuntimeStatusService runtime,
                                     ObjectProvider<TicketSearchPublishService> publishers,
                                     ObjectProvider<TicketProjectionService> projections, TicketIndexSourceMapper sources,
                                     CurrentUserIdProvider users, Validator validator, ObjectMapper json) {
        this.store=store; this.properties=properties; this.readiness=readiness; this.runtime=runtime;
        this.publishers=publishers; this.projections=projections; this.sources=sources;
        this.users=users; this.validator=validator; this.json=json;
    }

    /**
     * 不依赖本机历史 ready 标记，每次重新核对共享状态及外部目标。
     *
     * @return 工单搜索管理员状态
     */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchAdminStatus status() {
        TicketSearchAdminStatus status=new TicketSearchAdminStatus(); status.setSyncEnabled(properties.isEnabled());
        try {
            TicketSearchControlState control=store.state(); status.setControl(control);
            if (control != null && control.getCurrentJobId() != null) { status.setTask(store.task(control.getCurrentJobId())); }
            status.setReconciliation(store.reconciliation());
            readiness.requireReady(); status.setReady(true);
        } catch (Exception exception) { status.setError(exception.getClass().getSimpleName()); }
        // 就绪检查失败也继续采集积压，便于区分索引未发布和消息链路异常。
        status.setRuntime(runtime.inspect()); return status;
    }

    /**
     * 同一 requestId 永远返回原任务；新代次通过共享控制锁登记。
     *
     * @param request 工单搜索Create导入请求
     * @return 工单索引导入任务
     */
    public TicketSearchImportTask create(TicketSearchCreateImportRequest request) {
        enabled(); validate(request);
        // 这里只登记任务，历史扫描由定时工作者执行，接口不等待全量工单写入 ES。
        return store.create(request.getRequestId(),request.getReason().trim(),adminId());
    }

    /**
     * 查询指定任务，只返回数据库保存的事实。
     *
     * @param id 导入任务 ID
     * @return 工单索引导入任务
     */
    public TicketSearchImportTask task(Long id) { positive(id); return store.task(id); }

    /**
     * 失败扫描续跑原代次；PUBLISHING 则走人工确认后回读别名的恢复路径。
     *
     * @param id 导入任务 ID
     * @param request 工单搜索Resume请求
     * @return 工单索引导入任务
     * @throws IOException 处理过程中发生IO异常时
     */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchImportTask resume(Long id, TicketSearchResumeRequest request) throws IOException {
        enabled(); positive(id); if (request != null) { validate(request); }
        TicketSearchImportTask task=store.task(id);
        // 发布超时可能已经换过读别名，必须先回读 ES；普通扫描失败则从原游标续跑。
        if ("PUBLISHING".equals(task.getStatus())) { return publisher().recover(id,request,adminId()); }
        return store.resume(id);
    }

    /**
     * 发布请求逐项记录源实例部署核对，重复实例或超过存储上限的清单拒绝。
     *
     * @param id 导入任务 ID
     * @param request 工单搜索发布请求
     * @return 工单索引导入任务
     * @throws IOException 处理过程中发生IO异常时
     */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchImportTask publish(Long id, TicketSearchPublishRequest request) throws IOException {
        enabled(); positive(id); validate(request);
        // 部署确认需要覆盖每个源实例；同一实例重复填报不能算作多份证明。
        Set<String> ids=new HashSet<>();
        for (TicketSearchPublishRequest.SourceInstance source : request.getSourceInstances()) {
            if (!ids.add(source.getInstanceId().trim())) { illegal(); }
        }
        String manifest=json.writeValueAsString(request);
        if (manifest.length() > 2000) { illegal(); }
        return publisher().publish(id,adminId(),manifest);
    }

    /**
     * 单工单修复只能回源当前完整行和源版本，不接受自定义正文或版本。
     *
     * @param ticketId 工单 ID
     * @return 当前完整源行已写入，或目标已有相同及更高源版本的结果
     * @throws IOException 处理过程中发生IO异常时
     */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchWriteOutcome repair(Long ticketId) throws IOException {
        enabled(); positive(ticketId);
        TicketIndexSource source=sources.selectById(ticketId);
        if (source == null) { throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND); }
        TicketProjectionService projection=projections.getIfAvailable();
        if (projection == null) { unavailable(); }
        // 最终写入仍由统一投影再次回源，版本和完整内容必须来自同一条记录。
        return projection.synchronize(ticketId,source.getSourceVersion());
    }

    /**
     * 取得已启用的发布服务，否则报告搜索未就绪。
     *
     * @return 已启用且可用的索引发布服务；缺少依赖时抛出未就绪异常
     */
    private TicketSearchPublishService publisher() {
        TicketSearchPublishService publisher=publishers.getIfAvailable();
        if (publisher == null) { unavailable(); }
        return publisher;
    }

    /**
     * 读取并校验当前管理员的用户 ID。
     *
     * @return 已认证当前管理员的正整数用户 ID
     */
    private Long adminId() {
        Long id=users.get(SecurityContextHolder.getContext().getAuthentication()); positive(id); return id;
    }

    /**
     * 拒绝空请求及未通过 Bean Validation 的管理参数。
     *
     * @param request 需要通过 Bean Validation 的管理请求
     */
    private void validate(Object request) { if (request == null || !validator.validate(request).isEmpty()) { illegal(); } }
    /**
     * 确认索引同步已启用，否则报告搜索未就绪。
     */
    private void enabled() { if (!properties.isEnabled()) { unavailable(); } }
    /**
     * 拒绝空值或非正数的管理操作主键。
     *
     * @param id 必须为正数的导入任务或工单主键
     */
    private static void positive(Long id) { if (id == null || id <= 0) { illegal(); } }
    /**
     * 抛出统一的管理请求参数错误。
     */
    private static void illegal() { throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT); }
    /**
     * 抛出工单搜索未就绪的统一业务错误。
     */
    private static void unavailable() { throw new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY); }
}
