package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 催办事件 V1 的稳定业务快照。
 */
public final class TicketRemindedPayload {
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
     * 催办操作日志 ID。
     */
    private final long remindLogId;
    /**
     * 本工单累计催办次数。
     */
    private final int remindCount;
    /**
     * 催办操作人 ID。
     */
    private final long remindedBy;
    /**
     * 催办发生时间，包含时区偏移量。
     */
    private final OffsetDateTime remindedAt;
    /**
     * 可空的当前处理人 ID。
     */
    private final Long handlerId;
    /**
     * 事务内确定的通知接收人快照。
     */
    private final List<Long> receiverIds;

    /**
     * 保存校验后的催办快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param remindLogId 催办操作日志 ID
     * @param remindCount 本工单累计催办次数
     * @param remindedBy 催办操作人 ID
     * @param remindedAt 催办发生时间，包含时区偏移量
     * @param handlerId 可空的当前处理人 ID
     * @param receiverIds 事务内确定的通知接收人快照
     */
    private TicketRemindedPayload(long ticketId, String ticketNo, String ticketTitle,
                                  long remindLogId, int remindCount, long remindedBy,
                                  OffsetDateTime remindedAt, Long handlerId,
                                  List<Long> receiverIds) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.remindLogId = remindLogId;
        this.remindCount = remindCount;
        this.remindedBy = remindedBy;
        this.remindedAt = remindedAt;
        this.handlerId = handlerId;
        this.receiverIds = receiverIds;
    }

    /**
     * 校验催办次数、当前处理人和接收人后提取 V1 快照。
     *
     * @param event 携带催办动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及催办事实校验的稳定事件载荷
     */
    public static TicketRemindedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_REMINDED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        long remindLogId = TicketPayloadReader.positiveLong(payload, "remindLogId");
        int count = TicketPayloadReader.integer(payload, "remindCount", 1, 3);
        long actorId = TicketPayloadReader.positiveLong(payload, "remindedBy");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String title = TicketPayloadReader.optionalText(payload, "ticketTitle");
        Long handlerId = TicketPayloadReader.optionalPositiveLong(payload, "handlerId");
        OffsetDateTime remindedAt = TicketPayloadReader.time(payload, "remindedAt");
        List<Long> receivers = TicketPayloadReader.receivers(payload, actorId);
        TicketPayloadReader.identity(event, ticketId, actorId);
        if (handlerId != null && handlerId != actorId && !receivers.contains(handlerId)) {
            throw new IllegalArgumentException("催办事件接收人缺少当前处理人");
        }
        return new TicketRemindedPayload(ticketId, ticketNo, title, remindLogId,
                count, actorId, remindedAt, handlerId, receivers);
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
     * 获取催办操作日志 ID。
     *
     * @return 催办操作日志 ID
     */
    public long getRemindLogId() { return remindLogId; }
    /**
     * 获取本工单累计催办次数。
     *
     * @return 累计催办次数
     */
    public int getRemindCount() { return remindCount; }
    /**
     * 获取催办操作人 ID。
     *
     * @return 催办操作人 ID
     */
    public long getRemindedBy() { return remindedBy; }
    /**
     * 获取催办发生时间，包含时区偏移量。
     *
     * @return 带时区偏移量的催办时间
     */
    public OffsetDateTime getRemindedAt() { return remindedAt; }
    /**
     * 获取可空的当前处理人 ID。
     *
     * @return 可空的当前处理人 ID
     */
    public Long getHandlerId() { return handlerId; }
    /**
     * 获取事务内确定的通知接收人快照。
     *
     * @return 按事件顺序保存的只读接收人快照，已去重并排除操作人
     */
    public List<Long> getReceiverIds() { return receiverIds; }
}
