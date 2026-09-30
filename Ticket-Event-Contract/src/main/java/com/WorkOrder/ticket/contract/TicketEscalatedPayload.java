package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.EventVersion;
import com.WorkOrder.messaging.contract.WorkOrderEvent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 升级事件的 V1 业务快照，供通知和 SLA 消费端采用同一套校验规则。 */
public final class TicketEscalatedPayload {
    /** 工单主键。 */
    private final long ticketId;
    /** 工单编号。 */
    private final String ticketNo;
    /** 可空的工单标题。 */
    private final String ticketTitle;
    /** 当前处理人 ID，自动升级待分配工单时可为空。 */
    private final Long handlerId;
    /** 升级前级别。 */
    private final int fromEscalationLevel;
    /** 升级后级别。 */
    private final int escalationLevel;
    /** 升级后的 SLA 状态。 */
    private final String slaStatus;
    /** 升级时的工单状态。 */
    private final String status;
    /** 升级原因。 */
    private final String reason;
    /** 人工升级的处理人 ID，系统升级时为空。 */
    private final Long escalatedBy;
    /** AUTO 为系统升级；旧版事件缺省为 MANUAL。 */
    private final String escalationSource;
    /** 升级发生时间，包含时区偏移量。 */
    private final OffsetDateTime escalatedAt;
    /** 升级操作日志 ID。 */
    private final long escalationLogId;
    /** 事务内确定的部门负责人或管理员接收人快照。 */
    private final List<Long> receiverIds;
    /** 响应截止时间，供缺失 SLA 记录初始化。 */
    private final LocalDateTime responseDeadline;
    /** 解决截止时间，供缺失 SLA 记录初始化。 */
    private final LocalDateTime resolutionDeadline;

    /** 保存校验后的升级快照。 */
    private TicketEscalatedPayload(long ticketId, String ticketNo, String ticketTitle,
                                   Long handlerId, int fromEscalationLevel, int escalationLevel,
                                   String slaStatus, String status, String reason,
                                   Long escalatedBy, OffsetDateTime escalatedAt,
                                   long escalationLogId, List<Long> receiverIds,
                                   LocalDateTime responseDeadline, LocalDateTime resolutionDeadline,
                                   String escalationSource) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.handlerId = handlerId;
        this.fromEscalationLevel = fromEscalationLevel;
        this.escalationLevel = escalationLevel;
        this.slaStatus = slaStatus;
        this.status = status;
        this.reason = reason;
        this.escalatedBy = escalatedBy;
        this.escalationSource = escalationSource;
        this.escalatedAt = escalatedAt;
        this.escalationLogId = escalationLogId;
        this.receiverIds = Collections.unmodifiableList(receiverIds);
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /** 校验事件信封与升级事实一致，并提取两个消费者所需的稳定快照。 */
    public static TicketEscalatedPayload from(WorkOrderEvent event) {
        if (event == null || !EventType.TICKET_ESCALATED.name().equals(event.getEventType())
                || event.getEventVersion() != EventVersion.V1
                || !"TICKET".equals(event.getAggregateType())
                || event.getOccurredAt() == null) {
            throw new IllegalArgumentException("升级事件信封无效");
        }
        JsonNode payload = event.getPayload();
        long ticketId = positiveLong(payload, "ticketId");
        String ticketNo = requiredText(payload, "ticketNo");
        String ticketTitle = optionalText(payload, "ticketTitle");
        String source = payload != null && payload.has("escalationSource")
                ? requiredText(payload, "escalationSource") : "MANUAL";
        if (!"AUTO".equals(source) && !"MANUAL".equals(source)) {
            throw new IllegalArgumentException("升级来源无效");
        }
        boolean automatic = "AUTO".equals(source);
        Long handlerId = automatic && !payload.hasNonNull("handlerId") ? null : positiveLong(payload, "handlerId");
        Long escalatedBy = automatic ? null : positiveLong(payload, "escalatedBy");
        if (automatic && payload.hasNonNull("escalatedBy")) {
            throw new IllegalArgumentException("系统升级不能冒用用户身份");
        }
        long escalationLogId = positiveLong(payload, "escalationLogId");
        int fromLevel = level(payload, "fromEscalationLevel");
        int toLevel = level(payload, "escalationLevel");
        String reason = requiredText(payload, "reason");
        OffsetDateTime escalatedAt = dateTime(payload, "escalatedAt");
        LocalDateTime responseDeadline = dateTime(payload, "responseDeadline").toLocalDateTime();
        LocalDateTime resolutionDeadline = dateTime(payload, "resolutionDeadline").toLocalDateTime();
        List<Long> receiverIds = receiverIds(payload, escalatedBy);
        if (automatic && receiverIds.isEmpty()) {
            throw new IllegalArgumentException("自动升级必须包含接收人");
        }

        String slaStatus = requiredText(payload, "slaStatus");
        String status = requiredText(payload, "status");
        if (fromLevel < 0 || fromLevel >= 3 || toLevel > 3 || toLevel <= fromLevel
                || (!automatic && toLevel != fromLevel + 1)
                || !"ESCALATED".equals(slaStatus)
                || !(activeStatus(status) || (automatic && "PENDING_ASSIGN".equals(status)))
                || (handlerId == null && !"PENDING_ASSIGN".equals(status))) {
            throw new IllegalArgumentException("升级事件级别或状态无效");
        }
        if (!String.valueOf(ticketId).equals(event.getAggregateId())
                || (!automatic && !handlerId.equals(escalatedBy))
                || !(automatic ? "SYSTEM" : String.valueOf(escalatedBy)).equals(event.getActorId())
                || !event.getOccurredAt().isEqual(escalatedAt)) {
            throw new IllegalArgumentException("升级事件信封与业务快照不一致");
        }
        return new TicketEscalatedPayload(ticketId, ticketNo, ticketTitle, handlerId,
                fromLevel, toLevel, slaStatus, status, reason, escalatedBy, escalatedAt,
                escalationLogId, receiverIds, responseDeadline, resolutionDeadline, source);
    }

    /** 读取正整数 ID。 */
    private static long positiveLong(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("升级事件 payload." + field + " 必须为正整数");
        }
        return value.longValue();
    }

    /** 读取升级级别，范围由调用方结合前后级别校验。 */
    private static int level(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("升级事件 payload." + field + " 必须为整数");
        }
        return value.intValue();
    }

    /** 读取非空字符串。 */
    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("升级事件 payload." + field + " 不能为空");
        }
        return value.textValue().trim();
    }

    /** 读取可空标题。 */
    private static String optionalText(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || payload.get(field).isNull()) {
            return null;
        }
        JsonNode value = payload.get(field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("升级事件 payload." + field + " 必须为字符串");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    /** 读取带时区的发生时间或 SLA 截止时间。 */
    private static OffsetDateTime dateTime(JsonNode payload, String field) {
        String value = requiredText(payload, field);
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("升级事件 payload." + field + " 必须包含有效时区", exception);
        }
    }

    /** 检查并保留生产时已确定的接收人顺序。 */
    private static List<Long> receiverIds(JsonNode payload, Long escalatedBy) {
        JsonNode values = required(payload, "receiverIds");
        if (!values.isArray()) {
            throw new IllegalArgumentException("升级事件 payload.receiverIds 必须为数组");
        }
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()
                    || value.longValue() <= 0 || Long.valueOf(value.longValue()).equals(escalatedBy)
                    || !seen.add(value.longValue())) {
                throw new IllegalArgumentException("升级事件 payload.receiverIds 存在非法或重复接收人");
            }
            result.add(value.longValue());
        }
        return result;
    }

    /** 升级只作用于仍在处理中的工单。 */
    private static boolean activeStatus(String status) {
        return "PENDING_RESPONSE".equals(status) || "PROCESSING".equals(status);
    }

    /** 读取必需字段。 */
    private static JsonNode required(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            throw new IllegalArgumentException("升级事件缺少 payload." + field);
        }
        return payload.get(field);
    }

    /** @return 工单 ID */
    public long getTicketId() { return ticketId; }
    /** @return 工单编号 */
    public String getTicketNo() { return ticketNo; }
    /** @return 可空的工单标题 */
    public String getTicketTitle() { return ticketTitle; }
    /** @return 当前处理人 ID，自动升级未派单工单时可为空 */
    public Long getHandlerId() { return handlerId; }
    /** @return 升级前级别 */
    public int getFromEscalationLevel() { return fromEscalationLevel; }
    /** @return 升级后的级别 */
    public int getEscalationLevel() { return escalationLevel; }
    /** @return 升级后的 SLA 状态 */
    public String getSlaStatus() { return slaStatus; }
    /** @return 升级时的工单状态 */
    public String getStatus() { return status; }
    /** @return 升级原因 */
    public String getReason() { return reason; }
    /** @return 人工升级的处理人 ID，系统升级时为空 */
    public Long getEscalatedBy() { return escalatedBy; }
    /** @return 是否为自动升级 */
    public boolean isAutomatic() { return "AUTO".equals(escalationSource); }
    /** @return 带时区偏移量的升级时间 */
    public OffsetDateTime getEscalatedAt() { return escalatedAt; }
    /** @return 升级操作日志 ID */
    public long getEscalationLogId() { return escalationLogId; }
    /** @return 部门负责人或管理员接收人快照 */
    public List<Long> getReceiverIds() { return receiverIds; }
    /** @return 响应截止时间 */
    public LocalDateTime getResponseDeadline() { return responseDeadline; }
    /** @return 解决截止时间 */
    public LocalDateTime getResolutionDeadline() { return resolutionDeadline; }
}
