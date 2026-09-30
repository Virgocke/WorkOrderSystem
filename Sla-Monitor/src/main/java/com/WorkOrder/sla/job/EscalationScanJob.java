package com.WorkOrder.sla.job;

import com.WorkOrder.sla.service.EscalationScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界扫描任务；多实例请求允许重复，工单事务保证最终升级只执行一次。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
public class EscalationScanJob {
    /** 独立代理保证每页 Outbox 写入有数据库事务。 */
    private final EscalationScanService scanService;
    /** 上次成功页的 ID 游标，扫描失败时保留供下轮重试。 */
    private long afterId;

    /** 默认每 60 秒扫描一页，持续推进游标，结束后从头检查新超时工单。 */
    @Scheduled(fixedDelayString = "${work-order.sla.escalation.scan-interval-ms:60000}",
            initialDelayString = "${work-order.sla.escalation.initial-delay-ms:10000}")
    public void scan() {
        try {
            afterId = scanService.scanPage(afterId);
        } catch (RuntimeException exception) {
            log.error("自动升级扫描失败，下轮从工单游标 {} 重试", afterId, exception);
        }
    }
}
