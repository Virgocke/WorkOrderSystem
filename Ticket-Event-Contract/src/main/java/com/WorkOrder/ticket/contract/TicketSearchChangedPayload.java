package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

/** 工单搜索变更 V1 信号，只携带待回读的工单 ID，源版本保存在事件信封中。 */
public final class TicketSearchChangedPayload {
    /** 工单主键。 */
    private final long ticketId;

    /** 保存校验后的工单标识。 */
    private TicketSearchChangedPayload(long ticketId) {
        this.ticketId = ticketId;
    }

    /** 校验事件类型、契约版本、来源、聚合标识和源版本后读取变更信号。 */
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

    /** @return 待回读的工单主键 */
    public long getTicketId() {
        return ticketId;
    }
}
