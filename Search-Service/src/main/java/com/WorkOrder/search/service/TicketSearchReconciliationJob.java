package com.WorkOrder.search.service;

import com.WorkOrder.search.config.TicketSearchSyncProperties;
import com.WorkOrder.search.mapper.TicketIndexSourceMapper;
import com.WorkOrder.search.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** READY 代次持续补偿，每个完整轮次都从零开始，覆盖迟提交与终态消息遗漏。 */
@Component
@ConditionalOnProperty(prefix="work-order.elasticsearch.ticket-sync", name="enabled", havingValue="true")
public class TicketSearchReconciliationJob {
    private static final Logger LOGGER=LoggerFactory.getLogger(TicketSearchReconciliationJob.class);
    private final TicketSearchTaskStore store;
    private final TicketIndexSourceMapper sources;
    private final TicketSearchVerificationService verification;
    private final TicketSearchSyncProperties properties;

    /** 复用统一批次验证与主库短事务。 */
    public TicketSearchReconciliationJob(TicketSearchTaskStore store, TicketIndexSourceMapper sources,
                                        TicketSearchVerificationService verification, TicketSearchSyncProperties properties) {
        this.store=store; this.sources=sources; this.verification=verification; this.properties=properties;
    }

    /** 有预算续跑同一轮次，失败或重启后保留未确认批次。 */
    @Scheduled(fixedDelayString="${work-order.elasticsearch.ticket-sync.reconcile-delay-ms:30000}")
    public void runOnce() {
        String token=TicketSearchTaskStore.token(); Long generation=null;
        try {
            // 对账使用独立租约和游标，不改已完成导入任务的历史进度。
            TicketSearchReconciliationState state=store.claimReconciliation(token, properties.getImportLeaseSeconds());
            if (state == null) { return; }
            generation=state.getGeneration();
            TicketSearchWriteTarget target=store.task(generation).target();
            if (state.getScanUpperId() == null) {
                // 每轮单独记录有限上界；续跑时沿用原值，不让新增工单延长当前轮次。
                Long upper=sources.selectMaxId();
                store.initializeReconciliation(generation,token,upper == null ? 0 : upper);
            }
            for (int i=0; i<properties.getBatchesPerRun(); i++) {
                if (!store.renewReconciliation(generation,token,properties.getImportLeaseSeconds())) { return; }
                state=store.reconciliation(); long cursor=state.getLastTicketId();
                List<TicketIndexSource> batch=sources.selectBatch(cursor,state.getScanUpperId(),properties.getBatchSize());
                // 全轮完成才复位游标和上界，下一轮从零覆盖迟提交的小 ID 和漏发事件。
                if (batch.isEmpty()) {
                    store.completeReconciliation(generation, token, cursor);
                    return;
                }
                verification.verifyAndRepair(target,batch);
                // 修复整批成功后再推进；异常保留旧游标，避免漏过尚未修复的工单。
                store.advanceReconciliation(generation,token,cursor,batch.get(batch.size()-1).getTicketId());
            }
        } catch (TicketSearchTaskStore.LostLeaseException ignored) {
            LOGGER.info("工单对账代次或租约已改变，旧工作者停止提交进度");
        } catch (Exception exception) {
            if (generation != null) { store.failReconciliation(generation,token,TicketSearchImportJob.safeError(exception)); }
            LOGGER.warn("工单搜索对账失败，generation={}，type={}",generation,exception.getClass().getSimpleName());
        } finally { store.releaseReconciliation(token); }
    }
}
