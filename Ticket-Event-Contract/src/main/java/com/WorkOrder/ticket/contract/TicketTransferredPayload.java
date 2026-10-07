package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 转派事件 V1 的稳定业务快照，截止时间保留在事件中供后续 SLA 消费。
 */
public final class TicketTransferredPayload {
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
     * 转派前处理人 ID。
     */
    private final long fromHandlerId;
    /**
     * 转派后处理人 ID。
     */
    private final long toHandlerId;
    /**
     * 转派操作人 ID。
     */
    private final long transferredBy;
    /**
     * 转派操作人角色。
     */
    private final String transferredByRole;
    /**
     * 转派发生时间，包含时区偏移量。
     */
    private final OffsetDateTime transferredAt;
    /**
     * 转派时不变的工单状态。
     */
    private final String status;
    /**
     * 转派原因。
     */
    private final String reason;
    /**
     * 转派操作日志 ID。
     */
    private final long transferLogId;
    /**
     * 本次转派对应的分配记录 ID。
     */
    private final long assignmentRecordId;
    /**
     * 可空的响应截止时间。
     */
    private final OffsetDateTime responseDeadline;
    /**
     * 可空的解决截止时间。
     */
    private final OffsetDateTime resolutionDeadline;

    /**
     * 保存校验后的转派快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param fromHandlerId 转派前处理人 ID
     * @param toHandlerId 转派后处理人 ID
     * @param transferredBy 转派操作人 ID
     * @param transferredByRole 转派操作人角色
     * @param transferredAt 转派发生时间，包含时区偏移量
     * @param status 转派时不变的工单状态
     * @param reason 转派原因
     * @param transferLogId 转派操作日志 ID
     * @param assignmentRecordId 本次转派对应的分配记录 ID
     * @param responseDeadline 可空的响应截止时间
     * @param resolutionDeadline 可空的解决截止时间
     */
    private TicketTransferredPayload(long ticketId, String ticketNo, String ticketTitle,
                                     long fromHandlerId, long toHandlerId, long transferredBy,
                                     String transferredByRole, OffsetDateTime transferredAt,
                                     String status, String reason, long transferLogId,
                                     long assignmentRecordId, OffsetDateTime responseDeadline,
                                     OffsetDateTime resolutionDeadline) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.fromHandlerId = fromHandlerId;
        this.toHandlerId = toHandlerId;
        this.transferredBy = transferredBy;
        this.transferredByRole = transferredByRole;
        this.transferredAt = transferredAt;
        this.status = status;
        this.reason = reason;
        this.transferLogId = transferLogId;
        this.assignmentRecordId = assignmentRecordId;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /**
     * 校验责任人、角色与状态后提取 V1 快照。
     *
     * @param event 携带转派动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及转派事实校验的稳定事件载荷
     */
    public static TicketTransferredPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_TRANSFERRED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        long fromHandlerId = TicketPayloadReader.positiveLong(payload, "fromHandlerId");
        long toHandlerId = TicketPayloadReader.positiveLong(payload, "toHandlerId");
        long actorId = TicketPayloadReader.positiveLong(payload, "transferredBy");
        long transferLogId = TicketPayloadReader.positiveLong(payload, "transferLogId");
        long assignmentRecordId = TicketPayloadReader.positiveLong(payload, "assignmentRecordId");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String title = TicketPayloadReader.optionalText(payload, "ticketTitle");
        String role = TicketPayloadReader.text(payload, "transferredByRole");
        String status = TicketPayloadReader.text(payload, "status");
        String reason = TicketPayloadReader.text(payload, "reason");
        OffsetDateTime transferredAt = TicketPayloadReader.time(payload, "transferredAt");
        OffsetDateTime responseDeadline = TicketPayloadReader.optionalTime(payload, "responseDeadline");
        OffsetDateTime resolutionDeadline = TicketPayloadReader.optionalTime(payload, "resolutionDeadline");
        if (fromHandlerId == toHandlerId
                || !("PENDING_RESPONSE".equals(status) || "PROCESSING".equals(status))
                || !("HANDLER".equals(role) || "ADMIN".equals(role))
                || ("HANDLER".equals(role) && actorId != fromHandlerId)) {
            throw new IllegalArgumentException("转派事件责任人、角色或状态无效");
        }
        TicketPayloadReader.identity(event, ticketId, actorId);
        TicketPayloadReader.occurredAt(event, transferredAt);
        return new TicketTransferredPayload(ticketId, ticketNo, title, fromHandlerId,
                toHandlerId, actorId, role, transferredAt, status, reason,
                transferLogId, assignmentRecordId, responseDeadline, resolutionDeadline);
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
     * 获取转派前处理人 ID。
     *
     * @return 原处理人 ID
     */
    public long getFromHandlerId() { return fromHandlerId; }
    /**
     * 获取转派后处理人 ID。
     *
     * @return 新处理人 ID
     */
    public long getToHandlerId() { return toHandlerId; }
    /**
     * 获取转派操作人 ID。
     *
     * @return 转派操作人 ID
     */
    public long getTransferredBy() { return transferredBy; }
    /**
     * 获取转派操作人角色。
     *
     * @return 转派操作人角色
     */
    public String getTransferredByRole() { return transferredByRole; }
    /**
     * 获取转派发生时间，包含时区偏移量。
     *
     * @return 带时区偏移量的转派时间
     */
    public OffsetDateTime getTransferredAt() { return transferredAt; }
    /**
     * 获取转派时不变的工单状态。
     *
     * @return 转派时的工单状态
     */
    public String getStatus() { return status; }
    /**
     * 获取转派原因。
     *
     * @return 转派原因
     */
    public String getReason() { return reason; }
    /**
     * 获取转派操作日志 ID。
     *
     * @return 转派操作日志 ID
     */
    public long getTransferLogId() { return transferLogId; }
    /**
     * 获取本次转派对应的分配记录 ID。
     *
     * @return 对应的分配记录 ID
     */
    public long getAssignmentRecordId() { return assignmentRecordId; }
    /**
     * 获取可空的响应截止时间。
     *
     * @return 可空的响应截止时间
     */
    public OffsetDateTime getResponseDeadline() { return responseDeadline; }
    /**
     * 获取可空的解决截止时间。
     *
     * @return 可空的解决截止时间
     */
    public OffsetDateTime getResolutionDeadline() { return resolutionDeadline; }
}
