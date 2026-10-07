package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单搜索变更 V1 信号，只携带待回读的工单 ID，源版本保存在事件信封中。
 */
public final class TicketSearchChangedPayload {
    /**
     * 工单主键。
     */
    private final long ticketId;

    /**
     * 保存校验后的工单标识。
     *
     * @param ticketId 工单主键
     */
    private TicketSearchChangedPayload(long ticketId) {
        this.ticketId = ticketId;
    }

    /**
     * 校验事件类型、契约版本、来源、聚合标识和源版本后读取变更信号。
     *
     * @param event 由 ticket-service 产生、包含正数 aggregateVersion 的 V1 搜索变更信号
     * @return 仅含待回源工单 ID 的变更信号；完整投影由消费者读取当前主库构造
     */
    public static TicketSearchChangedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_SEARCH_CHANGED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        if (!"ticket-service".equals(event.getProducer())
                || !String.valueOf(ticketId).equals(event.getAggregateId())
                || event.getAggregateVersion() == null || event.getAggregateVersion() <= 0) {
            throw new IllegalArgumentException("工单搜索变更事件来源、聚合标识或源版本无效");
        }
        return new TicketSearchChangedPayload(ticketId);
    }

    /**
     * 获取工单主键。
     *
     * @return 待回读的工单主键
     */
    public long getTicketId() {
        return ticketId;
    }
}
