package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 将工单转派事件转换为新旧处理人的站内通知。 */
@Component
public class TicketTransferredNotificationHandler {

    static final String INTERNAL_CHANNEL = "INTERNAL";
    static final String SENT_STATUS = "SENT";

    private static final Set<String> TRANSFERABLE_STATUSES = new HashSet<>(
            Arrays.asList("PENDING_RESPONSE", "PROCESSING"));

    private final NotificationMapper notificationMapper;

    /**
     * 创建工单转派通知处理器。
     *
     * @param notificationMapper 通知数据访问组件
     */
    public TicketTransferredNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 校验 V1 转派事实并写入站内通知。
     * 新处理人一定接收；管理员代为转派时旧处理人也接收，主动转派人不自通知。
     * 调用方负责将全部通知写入与消费日志放在同一本地事务中。
     *
     * @param event 工单转派事件
     */
    public void handle(WorkOrderEvent event) {
        TransferPayload payload = validatePayload(event);
        LocalDateTime sentAt = LocalDateTime.now();

        insertNotification(
                event,
                payload,
                payload.getToHandlerId(),
                buildNewHandlerContent(payload),
                sentAt);

        if (payload.getTransferredBy() != payload.getFromHandlerId()) {
            insertNotification(
                    event,
                    payload,
                    payload.getFromHandlerId(),
                    buildOldHandlerContent(payload),
                    sentAt);
        }

        // 邮件、短信等外部渠道应继续通过本地投递任务扩展，避免阻塞消费事务。
    }

    /** 保存一条站内通知，失败时抛出异常以回滚本次消费日志和全部通知。 */
    private void insertNotification(WorkOrderEvent event,
                                    TransferPayload payload,
                                    long receiverId,
                                    String content,
                                    LocalDateTime sentAt) {
        Notifications notification = new Notifications();
        notification.setSourceEventId(event.getEventId());
        notification.setTicketId(payload.getTicketId());
        notification.setReceiverId(receiverId);
        notification.setChannel(INTERNAL_CHANNEL);
        notification.setContent(content);
        notification.setStatus(SENT_STATUS);
        notification.setSentAt(sentAt);
        if (notificationMapper.insert(notification) != 1) {
            throw new IllegalStateException("工单转派站内通知写入失败");
        }
    }

    /** 校验转派载荷及其与统一事件信封的一致性。 */
    private TransferPayload validatePayload(WorkOrderEvent event) {
        JsonNode payload = event.getPayload();
        long ticketId = requiredPositiveLong(payload, "ticketId");
        long fromHandlerId = requiredPositiveLong(payload, "fromHandlerId");
        long toHandlerId = requiredPositiveLong(payload, "toHandlerId");
        long transferredBy = requiredPositiveLong(payload, "transferredBy");
        requiredPositiveLong(payload, "transferLogId");
        requiredPositiveLong(payload, "assignmentRecordId");
        String ticketNo = requiredText(payload, "ticketNo");
        String ticketTitle = optionalText(payload, "ticketTitle");
        String transferredByRole = requiredText(payload, "transferredByRole");
        String status = requiredText(payload, "status");
        String reason = requiredText(payload, "reason");
        OffsetDateTime transferredAt = requiredOffsetDateTime(payload, "transferredAt");
        optionalOffsetDateTime(payload, "responseDeadline");
        optionalOffsetDateTime(payload, "resolutionDeadline");

        if (fromHandlerId == toHandlerId) {
            throw new IllegalArgumentException("转派事件的新旧处理人不能相同");
        }
        if (!TRANSFERABLE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("转派事件 payload.status 不允许转派");
        }
        if (!"HANDLER".equals(transferredByRole) && !"ADMIN".equals(transferredByRole)) {
            throw new IllegalArgumentException("转派事件 payload.transferredByRole 无效");
        }
        if ("HANDLER".equals(transferredByRole) && transferredBy != fromHandlerId) {
            throw new IllegalArgumentException("处理人只能转派自己负责的工单");
        }
        if (!String.valueOf(ticketId).equals(event.getAggregateId())) {
            throw new IllegalArgumentException("转派事件 aggregateId 与 payload.ticketId 不一致");
        }
        if (!String.valueOf(transferredBy).equals(event.getActorId())) {
            throw new IllegalArgumentException("转派事件 actorId 与 payload.transferredBy 不一致");
        }
        if (event.getOccurredAt() == null || !event.getOccurredAt().isEqual(transferredAt)) {
            throw new IllegalArgumentException("转派事件 occurredAt 与 payload.transferredAt 不一致");
        }

        return new TransferPayload(
                ticketId,
                ticketNo,
                ticketTitle,
                fromHandlerId,
                toHandlerId,
                transferredBy,
                reason);
    }

    /** 构建发送给新处理人的通知。 */
    private String buildNewHandlerContent(TransferPayload payload) {
        return "工单 " + ticketDescription(payload)
                + " 已转派给您，请及时处理。转派原因：" + payload.getReason();
    }

    /** 构建管理员强制转派时发送给旧处理人的通知。 */
    private String buildOldHandlerContent(TransferPayload payload) {
        return "工单 " + ticketDescription(payload)
                + " 已由管理员转派给其他处理人。转派原因：" + payload.getReason();
    }

    /** 构建工单编号与可选标题。 */
    private String ticketDescription(TransferPayload payload) {
        return payload.getTicketTitle() == null
                ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
    }

    /** 读取必填正整数长整型字段。 */
    private long requiredPositiveLong(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("转派事件 payload." + fieldName + " 必须为正整数");
        }
        return value.longValue();
    }

    /** 读取必填非空文本字段。 */
    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = requiredPayload(payload, fieldName);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("转派事件 payload." + fieldName + " 不能为空");
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
            throw new IllegalArgumentException("转派事件 payload." + fieldName + " 必须为字符串");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    /** 读取必填带时区时间字段。 */
    private OffsetDateTime requiredOffsetDateTime(JsonNode payload, String fieldName) {
        String value = requiredText(payload, fieldName);
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "转派事件 payload." + fieldName + " 必须包含有效时区", exception);
        }
    }

    /** 校验可空的带时区时间字段。 */
    private void optionalOffsetDateTime(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return;
        }
        JsonNode value = payload.get(fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("转派事件 payload." + fieldName + " 必须为字符串或 null");
        }
        try {
            OffsetDateTime.parse(value.textValue());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "转派事件 payload." + fieldName + " 必须包含有效时区", exception);
        }
    }

    /** 读取必填 JSON 字段。 */
    private JsonNode requiredPayload(JsonNode payload, String fieldName) {
        if (payload == null || !payload.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("转派事件缺少 payload." + fieldName);
        }
        return payload.get(fieldName);
    }

    /**
     * 已校验且仅供当前处理器使用的转派事件快照。
     */
    private static final class TransferPayload {
        /** 工单编号 */
        private final long ticketId;
        /** 工单编号 */
        private final String ticketNo;
        /** 工单标题 */
        private final String ticketTitle;
        /** 旧处理人编号 */
        private final long fromHandlerId;
        /** 新处理人编号 */
        private final long toHandlerId;
        /** 转派人编号 */
        private final long transferredBy;
        /** 转派原因 */
        private final String reason;

        private TransferPayload(long ticketId,
                                String ticketNo,
                                String ticketTitle,
                                long fromHandlerId,
                                long toHandlerId,
                                long transferredBy,
                                String reason) {
            this.ticketId = ticketId;
            this.ticketNo = ticketNo;
            this.ticketTitle = ticketTitle;
            this.fromHandlerId = fromHandlerId;
            this.toHandlerId = toHandlerId;
            this.transferredBy = transferredBy;
            this.reason = reason;
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

        private long getFromHandlerId() {
            return fromHandlerId;
        }

        private long getToHandlerId() {
            return toHandlerId;
        }

        private long getTransferredBy() {
            return transferredBy;
        }

        private String getReason() {
            return reason;
        }
    }
}
