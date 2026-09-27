package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 将工单回复事件转换为相关人员站内通知。 */
@Component
public class TicketRepliedNotificationHandler {

    static final String INTERNAL_CHANNEL = "INTERNAL";
    static final String SENT_STATUS = "SENT";
    static final String USER_REPLY = "USER_REPLY";
    static final String HANDLER_REPLY = "HANDLER_REPLY";

    private final NotificationMapper notificationMapper;

    /**
     * 创建工单回复通知处理器。
     *
     * @param notificationMapper 通知数据访问组件
     */
    public TicketRepliedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 校验 V1 回复快照，并为每个接收人写入一条站内通知。
     * 调用方负责把通知写入与消费日志放在同一本地事务中。
     *
     * @param event 工单回复事件
     */
    public void handle(WorkOrderEvent event) {
        ReplyPayload payload = validatePayload(event);
        LocalDateTime sentAt = LocalDateTime.now();

        for (Long receiverId : payload.getReceiverIds()) {
            Notifications notification = new Notifications();
            notification.setSourceEventId(event.getEventId());
            notification.setTicketId(payload.getTicketId());
            notification.setReceiverId(receiverId);
            notification.setChannel(INTERNAL_CHANNEL);
            notification.setContent(buildContent(payload));
            notification.setStatus(SENT_STATUS);
            notification.setSentAt(sentAt);

            if (notificationMapper.insert(notification) != 1) {
                throw new IllegalStateException("工单回复站内通知写入失败");
            }
        }

        // TODO 后续阶段通过本地投递任务扩展邮件、短信等外部渠道。
    }

    /**
     * 校验回复事件载荷及其与统一信封的一致性。
     *
     * @param event 工单回复事件
     * @return 已校验的回复快照
     */
    private ReplyPayload validatePayload(WorkOrderEvent event) {
        JsonNode payload = event.getPayload();
        long ticketId = requiredPositiveLong(payload, "ticketId");
        requiredPositiveLong(payload, "replyLogId");
        long replierId = requiredPositiveLong(payload, "replierId");
        String ticketNo = requiredText(payload, "ticketNo");
        String ticketTitle = optionalText(payload, "ticketTitle");
        String replyType = requiredText(payload, "replyType");
        requiredText(payload, "replierRole");
        validateReplyType(replyType);
        validateRepliedAt(requiredText(payload, "repliedAt"));
        boolean hasText = requiredBoolean(payload, "hasText");
        int attachmentCount = requiredNonNegativeInt(payload, "attachmentCount");
        if (!hasText && attachmentCount == 0) {
            throw new IllegalArgumentException("回复事件必须包含正文或附件");
        }
        List<Long> receiverIds = requiredReceiverIds(payload, replierId);

        if (!String.valueOf(ticketId).equals(event.getAggregateId())) {
            throw new IllegalArgumentException("回复事件 aggregateId 与 payload.ticketId 不一致");
        }
        if (!String.valueOf(replierId).equals(event.getActorId())) {
            throw new IllegalArgumentException("回复事件 actorId 与 payload.replierId 不一致");
        }

        return new ReplyPayload(ticketId, ticketNo, ticketTitle, replyType, receiverIds);
    }

    /**
     * 根据回复类型构建不包含回复正文的站内通知文案。
     *
     * @param payload 回复快照
     * @return 站内通知文案
     */
    private String buildContent(ReplyPayload payload) {
        String ticket = payload.getTicketTitle() == null
                ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        if (USER_REPLY.equals(payload.getReplyType())) {
            return "用户已回复工单 " + ticket + "，请及时处理。";
        }
        return "工单 " + ticket + " 有新的处理回复，请及时查看。";
    }

    /**
     * 读取并校验接收人列表，拒绝非法、重复和自通知接收人。
     *
     * @param payload 事件载荷
     * @param replierId 回复人 ID
     * @return 保持事件顺序的接收人列表
     */
    private List<Long> requiredReceiverIds(JsonNode payload, long replierId) {
        JsonNode value = requiredPayload(payload, "receiverIds");
        if (!value.isArray()) {
            throw new IllegalArgumentException("回复事件 payload.receiverIds 必须为数组");
        }
        List<Long> receiverIds = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();
        for (JsonNode receiver : value) {
            if (!receiver.isIntegralNumber() || !receiver.canConvertToLong()
                    || receiver.longValue() <= 0) {
                throw new IllegalArgumentException("回复事件 payload.receiverIds 必须包含正整数");
            }
            long receiverId = receiver.longValue();
            if (receiverId == replierId) {
                throw new IllegalArgumentException("回复事件不能通知回复者本人");
            }
            if (!uniqueIds.add(receiverId)) {
                throw new IllegalArgumentException("回复事件 payload.receiverIds 不能重复");
            }
            receiverIds.add(receiverId);
        }
        return receiverIds;
    }

    /**
     * 校验回复业务类型。
     *
     * @param replyType 回复业务类型
     */
    private void validateReplyType(String replyType) {
        if (!USER_REPLY.equals(replyType) && !HANDLER_REPLY.equals(replyType)) {
            throw new IllegalArgumentException("不支持的回复类型：" + replyType);
        }
    }

    /**
     * 校验带时区的回复发生时间。
     *
     * @param repliedAt 回复发生时间文本
     */
    private void validateRepliedAt(String repliedAt) {
        try {
            OffsetDateTime.parse(repliedAt);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("回复事件 payload.repliedAt 必须包含有效时区", exception);
        }
    }

    /**
     * 读取必填正整数长整型字段。
     *
     * @param payload 事件载荷
     * @param fieldName 字段名
     * @return 字段值
     */
    private long requiredPositiveLong(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("回复事件 payload." + fieldName + " 必须为正整数");
        }
        return value.longValue();
    }

    /**
     * 读取必填非负整数字段。
     *
     * @param payload 事件载荷
     * @param fieldName 字段名
     * @return 字段值
     */
    private int requiredNonNegativeInt(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException("回复事件 payload." + fieldName + " 必须为非负整数");
        }
        return value.intValue();
    }

    /**
     * 读取必填布尔字段。
     *
     * @param payload 事件载荷
     * @param fieldName 字段名
     * @return 字段值
     */
    private boolean requiredBoolean(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("回复事件 payload." + fieldName + " 必须为布尔值");
        }
        return value.booleanValue();
    }

    /**
     * 读取必填非空文本字段。
     *
     * @param payload 事件载荷
     * @param fieldName 字段名
     * @return 去除首尾空白的字段值
     */
    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("回复事件 payload." + fieldName + " 不能为空");
        }
        return value.textValue().trim();
    }

    /**
     * 读取可选文本字段。
     *
     * @param payload 事件载荷
     * @param fieldName 字段名
     * @return 去除首尾空白的字段值，不存在或为空白时返回 null
     */
    private String optionalText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = payload.get(fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("回复事件 payload." + fieldName + " 必须为字符串");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 读取必填 JSON 字段。
     *
     * @param payload 事件载荷
     * @param fieldName 字段名
     * @return JSON 字段节点
     */
    private JsonNode requiredPayload(JsonNode payload, String fieldName) {
        if (payload == null || !payload.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("回复事件缺少 payload." + fieldName);
        }
        return payload.get(fieldName);
    }

    /** 已校验且仅供当前处理器使用的回复事件快照。 */
    private static final class ReplyPayload {
        private final long ticketId;
        private final String ticketNo;
        private final String ticketTitle;
        private final String replyType;
        private final List<Long> receiverIds;

        /**
         * 创建回复事件快照。
         *
         * @param ticketId 工单 ID
         * @param ticketNo 工单编号
         * @param ticketTitle 工单标题
         * @param replyType 回复类型
         * @param receiverIds 通知接收人列表
         */
        private ReplyPayload(long ticketId, String ticketNo, String ticketTitle,
                             String replyType, List<Long> receiverIds) {
            this.ticketId = ticketId;
            this.ticketNo = ticketNo;
            this.ticketTitle = ticketTitle;
            this.replyType = replyType;
            this.receiverIds = receiverIds;
        }

        /** @return 工单 ID */
        private long getTicketId() {
            return ticketId;
        }

        /** @return 工单编号 */
        private String getTicketNo() {
            return ticketNo;
        }

        /** @return 工单标题 */
        private String getTicketTitle() {
            return ticketTitle;
        }

        /** @return 回复类型 */
        private String getReplyType() {
            return replyType;
        }

        /** @return 通知接收人列表 */
        private List<Long> getReceiverIds() {
            return receiverIds;
        }
    }
}
