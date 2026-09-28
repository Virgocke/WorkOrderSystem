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

/** 将工单催办事件转换为处理人和管理员站内通知。 */
@Component
public class TicketRemindedNotificationHandler {

    static final String INTERNAL_CHANNEL = "INTERNAL";
    static final String SENT_STATUS = "SENT";

    private final NotificationMapper notificationMapper;

    /**
     * 创建工单催办通知处理器。
     *
     * @param notificationMapper 通知数据访问组件
     */
    public TicketRemindedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 校验 V1 催办快照，并为每个接收人写入一条站内通知。
     * 调用方负责把全部通知写入与消费日志放在同一本地事务中。
     *
     * @param event 工单催办事件
     */
    public void handle(WorkOrderEvent event) {
        RemindPayload payload = validatePayload(event);
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
                throw new IllegalStateException("工单催办站内通知写入失败");
            }
        }

        // 后续若扩展邮件、短信，应通过本地投递任务处理，不阻塞 RocketMQ 消费事务。
    }

    /**
     * 校验催办载荷及其与统一事件信封的一致性。
     *
     * @param event 工单催办事件
     * @return 已校验的催办快照
     */
    private RemindPayload validatePayload(WorkOrderEvent event) {
        JsonNode payload = event.getPayload();
        long ticketId = requiredPositiveLong(payload, "ticketId");
        requiredPositiveLong(payload, "remindLogId");
        int remindCount = requiredRemindCount(payload);
        long remindedBy = requiredPositiveLong(payload, "remindedBy");
        String ticketNo = requiredText(payload, "ticketNo");
        String ticketTitle = optionalText(payload, "ticketTitle");
        Long handlerId = optionalPositiveLong(payload, "handlerId");
        validateRemindedAt(requiredText(payload, "remindedAt"));
        List<Long> receiverIds = requiredReceiverIds(payload, remindedBy);

        if (!String.valueOf(ticketId).equals(event.getAggregateId())) {
            throw new IllegalArgumentException("催办事件 aggregateId 与 payload.ticketId 不一致");
        }
        if (!String.valueOf(remindedBy).equals(event.getActorId())) {
            throw new IllegalArgumentException("催办事件 actorId 与 payload.remindedBy 不一致");
        }
        if (handlerId != null && handlerId != remindedBy && !receiverIds.contains(handlerId)) {
            throw new IllegalArgumentException("催办事件接收人缺少当前处理人");
        }

        return new RemindPayload(ticketId, ticketNo, ticketTitle, remindCount, receiverIds);
    }

    /** 根据催办次数构建不包含敏感内容的站内通知文案。 */
    private String buildContent(RemindPayload payload) {
        String ticket = payload.getTicketTitle() == null
                ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        return "用户第 " + payload.getRemindCount() + " 次催办工单 "
                + ticket + "，请及时关注处理进度。";
    }

    /** 读取并校验 1 至 3 的催办次数。 */
    private int requiredRemindCount(JsonNode payload) {
        JsonNode value = requiredPayload(payload, "remindCount");
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < 1 || value.intValue() > 3) {
            throw new IllegalArgumentException("催办事件 payload.remindCount 必须在 1 至 3 之间");
        }
        return value.intValue();
    }

    /** 读取并校验接收人列表，拒绝非法、重复和自通知接收人。 */
    private List<Long> requiredReceiverIds(JsonNode payload, long remindedBy) {
        JsonNode value = requiredPayload(payload, "receiverIds");
        if (!value.isArray()) {
            throw new IllegalArgumentException("催办事件 payload.receiverIds 必须为数组");
        }
        List<Long> receiverIds = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();
        for (JsonNode receiver : value) {
            if (!receiver.isIntegralNumber() || !receiver.canConvertToLong()
                    || receiver.longValue() <= 0) {
                throw new IllegalArgumentException("催办事件 payload.receiverIds 必须包含正整数");
            }
            long receiverId = receiver.longValue();
            if (receiverId == remindedBy) {
                throw new IllegalArgumentException("催办事件不能通知催办人本人");
            }
            if (!uniqueIds.add(receiverId)) {
                throw new IllegalArgumentException("催办事件 payload.receiverIds 不能重复");
            }
            receiverIds.add(receiverId);
        }
        return receiverIds;
    }

    /** 校验带时区的催办发生时间。 */
    private void validateRemindedAt(String remindedAt) {
        try {
            OffsetDateTime.parse(remindedAt);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("催办事件 payload.remindedAt 必须包含有效时区", exception);
        }
    }

    /** 读取必填正整数长整型字段。 */
    private long requiredPositiveLong(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("催办事件 payload." + fieldName + " 必须为正整数");
        }
        return value.longValue();
    }

    /** 读取可选正整数长整型字段。 */
    private Long optionalPositiveLong(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = payload.get(fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("催办事件 payload." + fieldName + " 必须为正整数或 null");
        }
        return value.longValue();
    }

    /** 读取必填非空文本字段。 */
    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("催办事件 payload." + fieldName + " 不能为空");
        }
        return value.textValue().trim();
    }

    /** 读取可选文本字段。 */
    private String optionalText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = payload.get(fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("催办事件 payload." + fieldName + " 必须为字符串");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    /** 读取必填 JSON 字段。 */
    private JsonNode requiredPayload(JsonNode payload, String fieldName) {
        if (payload == null || !payload.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("催办事件缺少 payload." + fieldName);
        }
        return payload.get(fieldName);
    }

    /** 已校验且仅供当前处理器使用的催办事件快照。 */
    private static final class RemindPayload {
        private final long ticketId;
        private final String ticketNo;
        private final String ticketTitle;
        private final int remindCount;
        private final List<Long> receiverIds;

        private RemindPayload(long ticketId, String ticketNo, String ticketTitle,
                              int remindCount, List<Long> receiverIds) {
            this.ticketId = ticketId;
            this.ticketNo = ticketNo;
            this.ticketTitle = ticketTitle;
            this.remindCount = remindCount;
            this.receiverIds = receiverIds;
        }

        private long getTicketId() {
            return ticketId;
        }

        private String getTicketNo() {
            return ticketNo;
        }

        private String getTicketTitle() {
            return ticketTitle;
        }

        private int getRemindCount() {
            return remindCount;
        }

        private List<Long> getReceiverIds() {
            return receiverIds;
        }
    }
}
