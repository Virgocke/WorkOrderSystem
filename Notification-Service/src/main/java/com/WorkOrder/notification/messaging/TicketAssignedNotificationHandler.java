package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将工单派单事件转换为处理人站内通知。 */
@Component
public class TicketAssignedNotificationHandler {

    // 内部渠道
    static final String INTERNAL_CHANNEL = "INTERNAL";
    // 已发送状态
    static final String SENT_STATUS = "SENT";

    private final NotificationMapper notificationMapper;

    /**
     * 创建派单通知处理器。
     *
     * @param notificationMapper 通知数据访问组件
     */
    public TicketAssignedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 校验 V1 业务快照并写入处理人站内通知。
     * 该方法由 {@link com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor}
     * 的同步业务动作调用，因此通知写入与消费日志共享同一本地事务。
     *
     * @param event 工单派单事件
     */
    public void handle(WorkOrderEvent event) {
        JsonNode payload = event.getPayload();
        long ticketId = requiredPositiveLong(payload, "ticketId");
        long handlerId = requiredPositiveLong(payload, "handlerId");
        long assignedBy = requiredPositiveLong(payload, "assignedBy");
        String ticketNo = requiredText(payload, "ticketNo");
        String ticketTitle = optionalText(payload, "ticketTitle");
        String reason = optionalText(payload, "reason");

        if (!String.valueOf(ticketId).equals(event.getAggregateId())) {
            throw new IllegalArgumentException("派单事件 aggregateId 与 payload.ticketId 不一致");
        }
        if (!String.valueOf(assignedBy).equals(event.getActorId())) {
            throw new IllegalArgumentException("派单事件 actorId 与 payload.assignedBy 不一致");
        }

        LocalDateTime now = LocalDateTime.now();
        Notifications notification = new Notifications();
        notification.setSourceEventId(event.getEventId());
        notification.setTicketId(ticketId);
        notification.setReceiverId(handlerId);
        notification.setChannel(INTERNAL_CHANNEL);
        notification.setContent(buildContent(ticketNo, ticketTitle, reason));
        notification.setStatus(SENT_STATUS);
        notification.setSentAt(now);

        if (notificationMapper.insert(notification) != 1) {
            throw new IllegalStateException("工单派单站内通知写入失败");
        }

        // TODO 后续阶段通过本地投递任务扩展邮件、短信等外部渠道。
    }

    /**
     * 构建站内通知内容。
     *
     * @param ticketNo   工单编号
     * @param ticketTitle 工单标题
     * @param reason     派单原因
     * @return 站内通知内容
     */
    private String buildContent(String ticketNo, String ticketTitle, String reason) {
        String ticketDescription = ticketTitle == null
                ? ticketNo : ticketNo + "（" + ticketTitle + "）";
        String content = "工单 " + ticketDescription + " 已分配给您，请及时处理。";
        return reason == null ? content : content + " 分配说明：" + reason;
    }

    /**
     * 校验并获取 payload 中的指定字段值。
     *
     * @param payload  payload
     * @param fieldName 字段名
     * @return 字段值
     */
    private long requiredPositiveLong(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("派单事件 payload." + fieldName + " 必须为正整数");
        }
        return value.longValue();
    }

    /**
     * 校验并获取 payload 中的指定字段值。
     *
     * @param payload  payload
     * @param fieldName 字段名
     * @return 字段值
     */
    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("派单事件 payload." + fieldName + " 不能为空");
        }
        return value.textValue().trim();
    }

    /**
     * 校验并获取 payload 中的指定字段值。
     *
     * @param payload  payload
     * @param fieldName 字段名
     * @return 字段值
     */
    private String optionalText(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = payload.get(fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("派单事件 payload." + fieldName + " 必须为字符串");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 校验并获取 payload 中的指定字段值。
     *
     * @param payload  payload
     * @param fieldName 字段名
     * @return 字段值
     */
    private JsonNode requiredPayload(JsonNode payload, String fieldName) {
        if (payload == null || !payload.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("派单事件缺少 payload." + fieldName);
        }
        return payload.get(fieldName);
    }
}
