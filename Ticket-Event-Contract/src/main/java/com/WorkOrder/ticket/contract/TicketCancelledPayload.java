package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/** 工单撤销事件的 V1 业务快照。 */
public final class TicketCancelledPayload {
    /** 工单主键。 */
    private final long ticketId;
    /** 工单编号。 */
    private final String ticketNo;
    /** 可空的工单标题。 */
    private final String ticketTitle;
    /** 创建人 ID。 */
    private final long creatorId;
    /** 撤销时的处理人 ID，可为空。 */
    private final Long handlerId;
    /** 撤销操作人 ID。 */
    private final long cancelledBy;
    /** 带偏移量的撤销时间。 */
    private final OffsetDateTime cancelledAt;
    /** 撤销前的工单状态。 */
    private final String fromStatus;
    /** 通知接收人快照。 */
    private final List<Long> receiverIds;
    /** 响应截止时间，供 SLA 记录缺失时初始化。 */
    private final OffsetDateTime responseDeadline;
    /** 解决截止时间，供 SLA 记录缺失时初始化。 */
    private final OffsetDateTime resolutionDeadline;

    /** 保存已校验的撤销事件快照。 */
    private TicketCancelledPayload(long ticketId, String ticketNo, String ticketTitle,
                                   long creatorId, Long handlerId, long cancelledBy,
                                   OffsetDateTime cancelledAt, String fromStatus,
                                   List<Long> receiverIds, OffsetDateTime responseDeadline,
                                   OffsetDateTime resolutionDeadline) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.creatorId = creatorId;
        this.handlerId = handlerId;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
        this.fromStatus = fromStatus;
        this.receiverIds = receiverIds;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /** 校验撤销前后状态、操作人、接收人与信封，提取 V1 快照。 */
    public static TicketCancelledPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_CANCELLED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String ticketTitle = TicketPayloadReader.optionalText(payload, "ticketTitle");
        long creatorId = TicketPayloadReader.positiveLong(payload, "creatorId");
        Long handlerId = TicketPayloadReader.optionalPositiveLong(payload, "handlerId");
        long cancelledBy = TicketPayloadReader.positiveLong(payload, "cancelledBy");
        OffsetDateTime cancelledAt = TicketPayloadReader.time(payload, "cancelledAt");
        String fromStatus = TicketPayloadReader.text(payload, "fromStatus");
        List<Long> receiverIds = TicketPayloadReader.receivers(payload, cancelledBy);
        OffsetDateTime responseDeadline = TicketPayloadReader.time(payload, "responseDeadline");
        OffsetDateTime resolutionDeadline = TicketPayloadReader.time(payload, "resolutionDeadline");
        if (!("PENDING_ASSIGN".equals(fromStatus) || "PENDING_RESPONSE".equals(fromStatus)
                || "PROCESSING".equals(fromStatus))
                || !"CANCELLED".equals(TicketPayloadReader.text(payload, "status"))
                || !TicketTerminalReceivers.matches(receiverIds, creatorId, handlerId, cancelledBy)) {
            throw new IllegalArgumentException("撤销事件状态或接收人无效");
        }
        TicketPayloadReader.identity(event, ticketId, cancelledBy);
        TicketPayloadReader.occurredAt(event, cancelledAt);
        return new TicketCancelledPayload(ticketId, ticketNo, ticketTitle, creatorId,
                handlerId, cancelledBy, cancelledAt, fromStatus, receiverIds,
                responseDeadline, resolutionDeadline);
    }

    /** @return 工单主键 */
    public long getTicketId() { return ticketId; }
    /** @return 工单编号 */
    public String getTicketNo() { return ticketNo; }
    /** @return 可空的工单标题 */
    public String getTicketTitle() { return ticketTitle; }
    /** @return 创建人 ID */
    public long getCreatorId() { return creatorId; }
    /** @return 可空的处理人 ID */
    public Long getHandlerId() { return handlerId; }
    /** @return 撤销操作人 ID */
    public long getCancelledBy() { return cancelledBy; }
    /** @return 带偏移量的撤销时间 */
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    /** @return 撤销前状态 */
    public String getFromStatus() { return fromStatus; }
    /** @return 通知接收人快照 */
    public List<Long> getReceiverIds() { return receiverIds; }
    /** @return 响应截止时间 */
    public OffsetDateTime getResponseDeadline() { return responseDeadline; }
    /** @return 解决截止时间 */
    public OffsetDateTime getResolutionDeadline() { return resolutionDeadline; }
}
