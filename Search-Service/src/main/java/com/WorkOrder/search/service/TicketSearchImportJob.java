package com.WorkOrder.search.service;

import com.WorkOrder.search.config.TicketSearchSyncProperties;
import com.WorkOrder.search.exception.TicketSearchBulkException;
import com.WorkOrder.search.mapper.TicketIndexSourceMapper;
import com.WorkOrder.search.model.*;
import com.WorkOrder.search.repository.TicketSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 有预算的历史扫描工作者；ES 调用之间才进入 TaskStore 的短事务。
 */
@Component
@ConditionalOnProperty(prefix="work-order.elasticsearch.ticket-sync", name="enabled", havingValue="true")
public class TicketSearchImportJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketSearchImportJob.class);
    private final String owner = "import-" + TicketSearchTaskStore.token();
    private final TicketSearchTaskStore store;
    private final TicketIndexSourceMapper sources;
    private final TicketSearchIndexService indexes;
    private final TicketProjectionService projection;
    private final TicketSearchVerificationService verification;
    private final TicketSearchRepository repository;
    private final TicketSearchSyncProperties properties;

    /**
     * 保存任务和既有投影依赖，不在构造时执行导入。
     *
     * @param store 使用短事务管理代次、扫描租约及游标的存储服务
     * @param sources 读取完整工单源行、源版本及有限扫描上界的 Mapper
     * @param indexes 创建并校验任务专属索引和写别名的服务
     * @param projection 统一转换完整源行并以外部源版本写入的投影服务
     * @param verification 读取目标版本并修复缺失或陈旧投影的验证服务
     * @param repository 刷新固定代次及执行版本化读写的搜索仓库
     * @param properties 工单搜索同步配置属性
     */
    public TicketSearchImportJob(TicketSearchTaskStore store, TicketIndexSourceMapper sources,
                                TicketSearchIndexService indexes, TicketProjectionService projection,
                                TicketSearchVerificationService verification, TicketSearchRepository repository,
                                TicketSearchSyncProperties properties) {
        this.store=store; this.sources=sources; this.indexes=indexes; this.projection=projection;
        this.verification=verification; this.repository=repository; this.properties=properties;
    }

    /**
     * 同一时刻每个任务只有一个租约持有者；失败不会使定时器停止。
     */
    @Scheduled(fixedDelayString="${work-order.elasticsearch.ticket-sync.import-delay-ms:1000}")
    public void runOnce() {
        TicketSearchImportTask claimed = null;
        try {
            claimed = store.claim(owner, properties.getImportLeaseSeconds());
            if (claimed == null) {
                return;
            }
            runClaimed(claimed);
        } catch (TicketSearchTaskStore.LostLeaseException ignored) {
            LOGGER.info("工单导入扫描租约已改变，旧工作者停止提交进度");
        } catch (Exception exception) {
            if (claimed != null) { store.fail(claimed.getId(), claimed.getLeaseToken(), safeError(exception), failedId(exception)); }
            LOGGER.warn("工单导入批次失败，jobId={}，type={}", claimed == null ? null : claimed.getId(), safeError(exception));
        } finally {
            if (claimed != null) { store.release(claimed.getId(), claimed.getLeaseToken()); }
        }
    }

    /**
     * 按固定批次数执行已领取的导入或验证任务，并续租和提交完整批次进度。
     *
     * @param claimed 本工作者已领取、带有效租约令牌的导入或验证任务
     * @throws Exception 租约任务执行过程中的所有异常
     */
    private void runClaimed(TicketSearchImportTask claimed) throws Exception {
        Long id = claimed.getId();
        String token = claimed.getLeaseToken();
        // 每次调度只处理有限批次；未完成的游标留在数据库，下一次调度接着跑。
        for (int i=0; i<properties.getBatchesPerRun(); i++) {
            if (!store.renew(id, token, properties.getImportLeaseSeconds())) { return; }
            TicketSearchImportTask task = store.task(id);
            if (task.getTargetInitializedAt() == null) {
                // 先确认索引和写别名可用，再登记写目标，增量消息随后才能写入同一代次。
                indexes.createManagedIndex(task.target());
                store.activate(id, token);
                task = store.task(id);
            }
            if (task.getScanStartedAt() == null) {
                // 写目标激活后再取 MAX(id)，固定本轮上界，避免持续新增工单让扫描无法结束。
                store.initializeScan(id, token, upper());
                task = store.task(id);
            }
            long cursor = task.getLastTicketId();
            List<TicketIndexSource> batch = sources.selectBatch(cursor, task.getScanUpperId(), properties.getBatchSize());
            if (batch.isEmpty()) {
                if ("IMPORTING".equals(task.getStatus())) {
                    // 导入跑完后重新取上界并从零验证，补查导入期间新增和并行更新的工单。
                    store.beginVerification(id, token, cursor, upper());
                    continue;
                }
                // 验证扫描结束还要 refresh；这里只记录验证完成，读别名仍等待管理员单独发布。
                indexes.validateWriteTarget(task.target());
                repository.refresh(task.target());
                store.verified(id, token, cursor);
                return;
            }
            if ("IMPORTING".equals(task.getStatus())) {
                // 导入与增量消息可以同时写，源版本比较保证旧扫描结果不会覆盖新状态。
                TicketSearchVerificationService.requireComplete(projection.writeBatch(task.target(), batch), batch.size());
            } else {
                verification.verifyAndRepair(task.target(), batch);
            }
            // 只有整批写入或验证成功才提交末尾游标；失败时下次仍重做这一批。
            store.advance(id, token, task.getStatus(), cursor, batch.get(batch.size()-1).getTicketId(), batch.size());
        }
    }

    /**
     * 取得本轮有限扫描的工单 ID 上界。
     *
     * @return 当前源表最大工单 ID；空表时为 0
     */
    private long upper() { Long result=sources.selectMaxId(); return result == null ? 0 : result; }

    /**
     * 只存异常类别，不把 ES 服务端正文、地址或凭据暴露在管理接口。
     *
     * @param exception 捕获的异常
     * @return 异常类的简单名称，用于不含外部响应正文的失败摘要
     */
    static String safeError(Exception exception) { return exception.getClass().getSimpleName(); }

    /**
     * Bulk 有明确失败项时额外记录第一个工单主键。
     *
     * @param exception 捕获的异常
     * @return Bulk 首个明确失败项的工单 ID；无法定位或转换时为 null
     */
    static Long failedId(Exception exception) {
        if (exception instanceof TicketSearchBulkException) {
            TicketSearchBulkResult result=((TicketSearchBulkException)exception).getResult();
            if (!result.getFailures().isEmpty()) {
                try { return Long.valueOf(result.getFailures().keySet().iterator().next()); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }
}
