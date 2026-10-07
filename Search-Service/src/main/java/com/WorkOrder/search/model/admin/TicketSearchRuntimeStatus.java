package com.WorkOrder.search.model.admin;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 状态与发布前校验共用的运行观测结果，不创建额外健康端点或持久健康表。
 */
@Getter
@Setter
public class TicketSearchRuntimeStatus {
    private String topic;
    private String consumerGroup;
    /**
     * 本次采集完成时间；DB 与 MQ 分步采集，并非跨系统原子快照。
     */
    private Instant collectedAt;
    private TicketSearchOutboxStatus outbox;
    private TicketSearchConsumerStatus consumer;
}
