package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单解决事件 V1 快照；方案正文保留在状态历史中，不进入消息。
 */
public final class TicketResolvedPayload {
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
     * 工单创建人 ID，也是通知的候选接收人。
     */
    private final long creatorId;
    /**
     * 解决时的工单处理人 ID。
     */
    private final long handlerId;
    /**
     * 实际提交解决的操作人 ID。
     */
    private final long resolvedBy;
    /**
     * 解决操作人角色。
     */
    private final String resolvedByRole;
    /**
     * 解决发生时间，包含时区偏移量。
     */
    private final OffsetDateTime resolvedAt;
    /**
     * 解决操作日志 ID。
     */
    private final long resolutionLogId;
    /**
     * 去除操作人本人后的通知接收人快照。
     */
    private final List<Long> receiverIds;
    /**
     * 响应截止时间，供缺失 SLA 记录初始化。
     */
    private final OffsetDateTime responseDeadline;
    /**
     * 解决截止时间，供缺失 SLA 记录初始化。
     */
    private final OffsetDateTime resolutionDeadline;

    /**
     * 保存校验后的解决快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param creatorId 工单创建人 ID，也是通知的候选接收人
     * @param handlerId 解决时的工单处理人 ID
     * @param resolvedBy 实际提交解决的操作人 ID
     * @param resolvedByRole 解决操作人角色
     * @param resolvedAt 解决发生时间，包含时区偏移量
     * @param resolutionLogId 解决操作日志 ID
     * @param receiverIds 去除操作人本人后的通知接收人快照
     * @param responseDeadline 响应截止时间，供缺失 SLA 记录初始化
     * @param resolutionDeadline 解决截止时间，供缺失 SLA 记录初始化
     */
    private TicketResolvedPayload(long ticketId, String ticketNo, String ticketTitle,
                                  long creatorId, long handlerId, long resolvedBy,
                                  String resolvedByRole, OffsetDateTime resolvedAt,
                                  long resolutionLogId, List<Long> receiverIds,
                                  OffsetDateTime responseDeadline, OffsetDateTime resolutionDeadline) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.creatorId = creatorId;
        this.handlerId = handlerId;
        this.resolvedBy = resolvedBy;
        this.resolvedByRole = resolvedByRole;
        this.resolvedAt = resolvedAt;
        this.resolutionLogId = resolutionLogId;
        this.receiverIds = receiverIds;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /**
     * 校验解决状态、操作人、接收人与信封后提取 V1 快照。
     *
     * @param event 携带解决动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及解决事实校验的稳定事件载荷
     */
    public static TicketResolvedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_RESOLVED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String ticketTitle = TicketPayloadReader.optionalText(payload, "ticketTitle");
        long creatorId = TicketPayloadReader.positiveLong(payload, "creatorId");
        long handlerId = TicketPayloadReader.positiveLong(payload, "handlerId");
        long resolvedBy = TicketPayloadReader.positiveLong(payload, "resolvedBy");
        String role = TicketPayloadReader.text(payload, "resolvedByRole");
        String fromStatus = TicketPayloadReader.text(payload, "fromStatus");
        String status = TicketPayloadReader.text(payload, "status");
        OffsetDateTime resolvedAt = TicketPayloadReader.time(payload, "resolvedAt");
        long resolutionLogId = TicketPayloadReader.positiveLong(payload, "resolutionLogId");
        List<Long> receiverIds = TicketPayloadReader.receivers(payload, resolvedBy);
        OffsetDateTime responseDeadline = TicketPayloadReader.time(payload, "responseDeadline");
        OffsetDateTime resolutionDeadline = TicketPayloadReader.time(payload, "resolutionDeadline");
        if (!"PROCESSING".equals(fromStatus) || !"RESOLVED".equals(status)
                || !("HANDLER".equals(role) || "ADMIN".equals(role))
                || ("HANDLER".equals(role) && resolvedBy != handlerId)
                || receiverIds.size() > 1
                || (!receiverIds.isEmpty() && receiverIds.get(0) != creatorId)
                || (receiverIds.isEmpty() && creatorId != resolvedBy)) {
            throw new IllegalArgumentException("解决事件状态、操作人或接收人无效");
        }
        TicketPayloadReader.identity(event, ticketId, resolvedBy);
        TicketPayloadReader.occurredAt(event, resolvedAt);
        return new TicketResolvedPayload(ticketId, ticketNo, ticketTitle, creatorId,
                handlerId, resolvedBy, role, resolvedAt, resolutionLogId,
                receiverIds, responseDeadline, resolutionDeadline);
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
     * 获取工单创建人 ID，也是通知的候选接收人。
     *
     * @return 工单创建人 ID
     */
    public long getCreatorId() { return creatorId; }
    /**
     * 获取解决时的工单处理人 ID。
     *
     * @return 解决时的处理人 ID
     */
    public long getHandlerId() { return handlerId; }
    /**
     * 获取实际提交解决的操作人 ID。
     *
     * @return 解决操作人 ID
     */
    public long getResolvedBy() { return resolvedBy; }
    /**
     * 获取解决操作人角色。
     *
     * @return 解决操作人角色
     */
    public String getResolvedByRole() { return resolvedByRole; }
    /**
     * 获取解决发生时间，包含时区偏移量。
     *
     * @return 带时区偏移量的解决时间
     */
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    /**
     * 获取解决操作日志 ID。
     *
     * @return 解决操作日志 ID
     */
    public long getResolutionLogId() { return resolutionLogId; }
    /**
     * 获取去除操作人本人后的通知接收人快照。
     *
     * @return 按事件顺序保存的只读接收人快照，已去重并排除操作人
     */
    public List<Long> getReceiverIds() { return receiverIds; }
    /**
     * 获取响应截止时间，供缺失 SLA 记录初始化。
     *
     * @return 响应截止时间
     */
    public OffsetDateTime getResponseDeadline() { return responseDeadline; }
    /**
     * 获取解决截止时间，供缺失 SLA 记录初始化。
     *
     * @return 解决截止时间
     */
    public OffsetDateTime getResolutionDeadline() { return resolutionDeadline; }
}
