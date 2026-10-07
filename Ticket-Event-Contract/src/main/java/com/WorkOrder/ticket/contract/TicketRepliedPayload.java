package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 回复事件 V1 的稳定业务快照，不携带回复正文和附件地址。
 */
public final class TicketRepliedPayload {
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
     * 已持久化的回复操作日志 ID。
     */
    private final long replyLogId;
    /**
     * 回复动作类型：USER_REPLY 或 HANDLER_REPLY。
     */
    private final String replyType;
    /**
     * 回复人 ID。
     */
    private final long replierId;
    /**
     * 回复人角色。
     */
    private final String replierRole;
    /**
     * 回复发生时间，包含时区偏移量。
     */
    private final OffsetDateTime repliedAt;
    /**
     * 回复是否包含正文，不保存正文内容。
     */
    private final boolean hasText;
    /**
     * 回复所带附件数量，不保存附件地址。
     */
    private final int attachmentCount;
    /**
     * 事务内确定的通知接收人快照。
     */
    private final List<Long> receiverIds;

    /**
     * 保存校验后的回复快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param replyLogId 已持久化的回复操作日志 ID
     * @param replyType 回复动作类型：USER_REPLY 或 HANDLER_REPLY
     * @param replierId 回复人 ID
     * @param replierRole 回复人角色
     * @param repliedAt 回复发生时间，包含时区偏移量
     * @param hasText 回复是否包含正文，不保存正文内容
     * @param attachmentCount 回复所带附件数量，不保存附件地址
     * @param receiverIds 事务内确定的通知接收人快照
     */
    private TicketRepliedPayload(long ticketId, String ticketNo, String ticketTitle,
                                 long replyLogId, String replyType, long replierId,
                                 String replierRole, OffsetDateTime repliedAt, boolean hasText,
                                 int attachmentCount, List<Long> receiverIds) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.replyLogId = replyLogId;
        this.replyType = replyType;
        this.replierId = replierId;
        this.replierRole = replierRole;
        this.repliedAt = repliedAt;
        this.hasText = hasText;
        this.attachmentCount = attachmentCount;
        this.receiverIds = receiverIds;
    }

    /**
     * 校验回复事件信封、内容摘要和接收人，并提取 V1 快照。
     *
     * @param event 携带回复动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及回复事实校验的稳定事件载荷
     */
    public static TicketRepliedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_REPLIED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        long replyLogId = TicketPayloadReader.positiveLong(payload, "replyLogId");
        long replierId = TicketPayloadReader.positiveLong(payload, "replierId");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String title = TicketPayloadReader.optionalText(payload, "ticketTitle");
        String replyType = TicketPayloadReader.text(payload, "replyType");
        String replierRole = TicketPayloadReader.text(payload, "replierRole");
        OffsetDateTime repliedAt = TicketPayloadReader.time(payload, "repliedAt");
        if (!"USER_REPLY".equals(replyType) && !"HANDLER_REPLY".equals(replyType)) {
            throw new IllegalArgumentException("不支持的回复类型：" + replyType);
        }
        JsonNode hasText = TicketPayloadReader.required(payload, "hasText");
        if (!hasText.isBoolean()) {
            throw new IllegalArgumentException("payload.hasText 必须为布尔值");
        }
        int attachmentCount = TicketPayloadReader.integer(payload, "attachmentCount", 0, Integer.MAX_VALUE);
        if (!hasText.booleanValue() && attachmentCount == 0) {
            throw new IllegalArgumentException("回复事件必须包含正文或附件");
        }
        List<Long> receivers = TicketPayloadReader.receivers(payload, replierId);
        TicketPayloadReader.identity(event, ticketId, replierId);
        return new TicketRepliedPayload(ticketId, ticketNo, title, replyLogId, replyType,
                replierId, replierRole, repliedAt, hasText.booleanValue(), attachmentCount, receivers);
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
     * 获取已持久化的回复操作日志 ID。
     *
     * @return 回复操作日志 ID
     */
    public long getReplyLogId() { return replyLogId; }
    /**
     * 获取回复动作类型：USER_REPLY 或 HANDLER_REPLY。
     *
     * @return 回复动作类型
     */
    public String getReplyType() { return replyType; }
    /**
     * 获取回复人 ID。
     *
     * @return 回复人 ID
     */
    public long getReplierId() { return replierId; }
    /**
     * 获取回复人角色。
     *
     * @return 回复人角色
     */
    public String getReplierRole() { return replierRole; }
    /**
     * 获取回复发生时间，包含时区偏移量。
     *
     * @return 带时区偏移量的回复时间
     */
    public OffsetDateTime getRepliedAt() { return repliedAt; }
    /**
     * 判断回复动作是否包含正文。
     *
     * @return 回复动作是否包含正文内容；该快照仅保留布尔摘要
     */
    public boolean hasText() { return hasText; }
    /**
     * 获取回复所带附件数量，不保存附件地址。
     *
     * @return 回复附件数量
     */
    public int getAttachmentCount() { return attachmentCount; }
    /**
     * 获取事务内确定的通知接收人快照。
     *
     * @return 按事件顺序保存的只读接收人快照，已去重并排除操作人
     */
    public List<Long> getReceiverIds() { return receiverIds; }
}
