package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单撤销事件的 V1 业务快照。
 */
public final class TicketCancelledPayload {
    /**
     * 工单主键。
     */
    private final long ticketId;
    /**
     * 工单编号。
     */
    private final String ticketNo;
    /**
     * 可空的工单标题。
     */
    private final String ticketTitle;
    /**
     * 创建人 ID。
     */
    private final long creatorId;
    /**
     * 撤销时的处理人 ID，可为空。
     */
    private final Long handlerId;
    /**
     * 撤销操作人 ID。
     */
    private final long cancelledBy;
    /**
     * 带偏移量的撤销时间。
     */
    private final OffsetDateTime cancelledAt;
    /**
     * 撤销前的工单状态。
     */
    private final String fromStatus;
    /**
     * 通知接收人快照。
     */
    private final List<Long> receiverIds;
    /**
     * 响应截止时间，供 SLA 记录缺失时初始化。
     */
    private final OffsetDateTime responseDeadline;
    /**
     * 解决截止时间，供 SLA 记录缺失时初始化。
     */
    private final OffsetDateTime resolutionDeadline;

    /**
     * 保存已校验的撤销事件快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param creatorId 创建人 ID
     * @param handlerId 撤销时的处理人 ID，可为空
     * @param cancelledBy 撤销操作人 ID
     * @param cancelledAt 带偏移量的撤销时间
     * @param fromStatus 撤销前的工单状态
     * @param receiverIds 通知接收人快照
     * @param responseDeadline 响应截止时间，供 SLA 记录缺失时初始化
     * @param resolutionDeadline 解决截止时间，供 SLA 记录缺失时初始化
     */
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

    /**
     * 校验撤销前后状态、操作人、接收人与信封，提取 V1 快照。
     *
     * @param event 携带撤销动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及撤销事实校验的稳定事件载荷
     */
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

    /**
     * 获取工单主键。
     *
     * @return 工单主键
     */
    public long getTicketId() { return ticketId; }
    /**
     * 获取工单编号。
     *
     * @return 工单编号
     */
    public String getTicketNo() { return ticketNo; }
    /**
     * 获取可空的工单标题。
     *
     * @return 可空的工单标题
     */
    public String getTicketTitle() { return ticketTitle; }
    /**
     * 获取创建人 ID。
     *
     * @return 创建人 ID
     */
    public long getCreatorId() { return creatorId; }
    /**
     * 获取撤销时的处理人 ID，可为空。
     *
     * @return 可空的处理人 ID
     */
    public Long getHandlerId() { return handlerId; }
    /**
     * 获取撤销操作人 ID。
     *
     * @return 撤销操作人 ID
     */
    public long getCancelledBy() { return cancelledBy; }
    /**
     * 获取带偏移量的撤销时间。
     *
     * @return 带偏移量的撤销时间
     */
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    /**
     * 获取撤销前的工单状态。
     *
     * @return 撤销前状态
     */
    public String getFromStatus() { return fromStatus; }
    /**
     * 获取通知接收人快照。
     *
     * @return 按事件顺序保存的只读接收人快照，已去重并排除操作人
     */
    public List<Long> getReceiverIds() { return receiverIds; }
    /**
     * 获取响应截止时间，供 SLA 记录缺失时初始化。
     *
     * @return 响应截止时间
     */
    public OffsetDateTime getResponseDeadline() { return responseDeadline; }
    /**
     * 获取解决截止时间，供 SLA 记录缺失时初始化。
     *
     * @return 解决截止时间
     */
    public OffsetDateTime getResolutionDeadline() { return resolutionDeadline; }
}
