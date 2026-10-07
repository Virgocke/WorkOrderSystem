package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 用户确认关闭工单的 V1 业务快照。
 */
public final class TicketClosedPayload {
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
     * 关闭时的处理人 ID，可为空。
     */
    private final Long handlerId;
    /**
     * 确认关闭的操作人 ID。
     */
    private final long closedBy;
    /**
     * 带偏移量的关闭时间。
     */
    private final OffsetDateTime closedAt;
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
     * 保存已校验的关闭事件快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param creatorId 创建人 ID
     * @param handlerId 关闭时的处理人 ID，可为空
     * @param closedBy 确认关闭的操作人 ID
     * @param closedAt 带偏移量的关闭时间
     * @param receiverIds 通知接收人快照
     * @param responseDeadline 响应截止时间，供 SLA 记录缺失时初始化
     * @param resolutionDeadline 解决截止时间，供 SLA 记录缺失时初始化
     */
    private TicketClosedPayload(long ticketId, String ticketNo, String ticketTitle,
                                long creatorId, Long handlerId, long closedBy,
                                OffsetDateTime closedAt, List<Long> receiverIds,
                                OffsetDateTime responseDeadline, OffsetDateTime resolutionDeadline) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.creatorId = creatorId;
        this.handlerId = handlerId;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.receiverIds = receiverIds;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /**
     * 校验关闭状态、操作人、接收人和信封，提取 V1 快照。
     *
     * @param event 携带关闭动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及关闭事实校验的稳定事件载荷
     */
    public static TicketClosedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_CLOSED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String ticketTitle = TicketPayloadReader.optionalText(payload, "ticketTitle");
        long creatorId = TicketPayloadReader.positiveLong(payload, "creatorId");
        Long handlerId = TicketPayloadReader.optionalPositiveLong(payload, "handlerId");
        long closedBy = TicketPayloadReader.positiveLong(payload, "closedBy");
        OffsetDateTime closedAt = TicketPayloadReader.time(payload, "closedAt");
        List<Long> receiverIds = TicketPayloadReader.receivers(payload, closedBy);
        OffsetDateTime responseDeadline = TicketPayloadReader.time(payload, "responseDeadline");
        OffsetDateTime resolutionDeadline = TicketPayloadReader.time(payload, "resolutionDeadline");
        if (!"RESOLVED".equals(TicketPayloadReader.text(payload, "fromStatus"))
                || !"CLOSED".equals(TicketPayloadReader.text(payload, "status"))
                || !TicketTerminalReceivers.matches(receiverIds, creatorId, handlerId, closedBy)) {
            throw new IllegalArgumentException("关闭事件状态或接收人无效");
        }
        TicketPayloadReader.identity(event, ticketId, closedBy);
        TicketPayloadReader.occurredAt(event, closedAt);
        return new TicketClosedPayload(ticketId, ticketNo, ticketTitle, creatorId,
                handlerId, closedBy, closedAt, receiverIds, responseDeadline, resolutionDeadline);
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
     * 获取关闭时的处理人 ID，可为空。
     *
     * @return 可空的处理人 ID
     */
    public Long getHandlerId() { return handlerId; }
    /**
     * 获取确认关闭的操作人 ID。
     *
     * @return 关闭操作人 ID
     */
    public long getClosedBy() { return closedBy; }
    /**
     * 获取带偏移量的关闭时间。
     *
     * @return 带偏移量的关闭时间
     */
    public OffsetDateTime getClosedAt() { return closedAt; }
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
