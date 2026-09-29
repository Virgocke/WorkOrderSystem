package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

/** 派单事件 V1 的稳定业务快照。 */
public final class TicketAssignedPayload {
    /** 工单主键。 */
    private final long ticketId;
    /** 工单编号。 */
    private final String ticketNo;
    /** 可空的工单标题。 */
    private final String ticketTitle;
    /** 新处理人 ID。 */
    private final long handlerId;
    /** 手动派单操作人 ID；系统派单时为空。 */
    private final Long assignedBy;
    /** 派单操作者类型，ADMIN 或 SYSTEM。 */
    private final String assignedByRole;
    /** 兼容首期事件格式的可空派单时间文本。 */
    private final String assignedAt;
    /** 可空的派单说明。 */
    private final String reason;

    /** 保存校验后的派单快照。 */
    private TicketAssignedPayload(long ticketId, String ticketNo, String ticketTitle,
                                  long handlerId, Long assignedBy, String assignedByRole,
                                  String assignedAt, String reason) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.handlerId = handlerId;
        this.assignedBy = assignedBy;
        this.assignedByRole = assignedByRole;
        this.assignedAt = assignedAt;
        this.reason = reason;
    }

    /** 保持首期 V1 兼容：assignedAt 与展示字段均可缺省。 */
    public static TicketAssignedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_ASSIGNED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        long handlerId = TicketPayloadReader.positiveLong(payload, "handlerId");
        String role = TicketPayloadReader.optionalText(payload, "assignedByRole");
        if (role == null) {
            role = "ADMIN";
        }
        Long actorId;
        if ("SYSTEM".equals(role)) {
            if (payload.hasNonNull("assignedBy") || !"SYSTEM".equals(event.getActorId())) {
                throw new IllegalArgumentException("系统派单事件操作人无效");
            }
            actorId = null;
        } else if ("ADMIN".equals(role)) {
            actorId = TicketPayloadReader.positiveLong(payload, "assignedBy");
            TicketPayloadReader.identity(event, ticketId, actorId);
        } else {
            throw new IllegalArgumentException("不支持的派单操作人类型");
        }
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String ticketTitle = TicketPayloadReader.optionalText(payload, "ticketTitle");
        String reason = TicketPayloadReader.optionalText(payload, "reason");
        // 兼容首期已投递的本地时间文本与缺省 assignedAt。
        String assignedAt = TicketPayloadReader.optionalText(payload, "assignedAt");
        if (!String.valueOf(ticketId).equals(event.getAggregateId())) {
            throw new IllegalArgumentException("派单事件聚合 ID 无效");
        }
        return new TicketAssignedPayload(ticketId, ticketNo, ticketTitle,
                handlerId, actorId, role, assignedAt, reason);
    }

    /** @return 工单主键 */
    public long getTicketId() { return ticketId; }
    /** @return 工单编号 */
    public String getTicketNo() { return ticketNo; }
    /** @return 可空的工单标题 */
    public String getTicketTitle() { return ticketTitle; }
    /** @return 新处理人 ID */
    public long getHandlerId() { return handlerId; }
    /** @return 手动派单操作人 ID，系统派单时为空 */
    public Long getAssignedBy() { return assignedBy; }
    /** @return 派单操作人类型，ADMIN 或 SYSTEM */
    public String getAssignedByRole() { return assignedByRole; }
    /** @return 兼容首期格式的可空派单时间文本 */
    public String getAssignedAt() { return assignedAt; }
    /** @return 可空的派单说明 */
    public String getReason() { return reason; }
}
