package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.EventVersion;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 工单事件 V1 快照共享的字段与信封校验，不包含通知或 SLA 业务动作。 */
final class TicketPayloadReader {
    /** 工具类不允许实例化。 */
    private TicketPayloadReader() { }

    /** 校验事件类型、版本与聚合类型，并取得对象形式的载荷。 */
    static JsonNode payload(WorkOrderEvent event, EventType type) {
        if (event == null || !type.name().equals(event.getEventType())
                || event.getEventVersion() != EventVersion.V1
                || !"TICKET".equals(event.getAggregateType()) || event.getPayload() == null
                || !event.getPayload().isObject()) {
            throw new IllegalArgumentException(type + " 事件信封无效");
        }
        return event.getPayload();
    }

    /** 校验聚合 ID 和操作人 ID 与载荷一致。 */
    static void identity(WorkOrderEvent event, long ticketId, long actorId) {
        if (!String.valueOf(ticketId).equals(event.getAggregateId())
                || !String.valueOf(actorId).equals(event.getActorId())) {
            throw new IllegalArgumentException("工单事件信封与业务快照不一致");
        }
    }

    /** 校验事件发生时间与业务动作时间指向同一时刻。 */
    static void occurredAt(WorkOrderEvent event, OffsetDateTime value) {
        if (event.getOccurredAt() == null || !event.getOccurredAt().isEqual(value)) {
            throw new IllegalArgumentException("工单事件发生时间与业务快照不一致");
        }
    }

    /** 读取必须存在且非 null 的载荷字段。 */
    static JsonNode required(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            throw new IllegalArgumentException("工单事件缺少 payload." + field);
        }
        return payload.get(field);
    }

    /** 读取正整数 ID。 */
    static long positiveLong(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("payload." + field + " 必须为正整数");
        }
        return value.longValue();
    }

    /** 读取可空 ID；存在时必须为正整数。 */
    static Long optionalPositiveLong(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        return positiveLong(payload, field);
    }

    /** 读取指定闭区间内的整数字段。 */
    static int integer(JsonNode payload, String field, int min, int max) {
        JsonNode value = required(payload, field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < min || value.intValue() > max) {
            throw new IllegalArgumentException("payload." + field + " 不在允许范围内");
        }
        return value.intValue();
    }

    /** 读取非空白字符串。 */
    static String text(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("payload." + field + " 不能为空");
        }
        return value.textValue().trim();
    }

    /** 读取可空字符串，并统一去除首尾空白。 */
    static String optionalText(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        JsonNode value = payload.get(field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("payload." + field + " 必须为字符串");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    /** 读取带时区偏移量的时间。 */
    static OffsetDateTime time(JsonNode payload, String field) {
        try {
            return OffsetDateTime.parse(text(payload, field));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("payload." + field + " 必须包含有效时区", exception);
        }
    }

    /** 读取可空的带时区偏移量时间。 */
    static OffsetDateTime optionalTime(JsonNode payload, String field) {
        return payload == null || !payload.hasNonNull(field) ? null : time(payload, field);
    }

    /** 校验接收人无重复且不包含操作人，保留快照原有顺序。 */
    static List<Long> receivers(JsonNode payload, long actorId) {
        JsonNode values = required(payload, "receiverIds");
        if (!values.isArray()) {
            throw new IllegalArgumentException("payload.receiverIds 必须为数组");
        }
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()
                    || value.longValue() <= 0 || value.longValue() == actorId
                    || !seen.add(value.longValue())) {
                throw new IllegalArgumentException("payload.receiverIds 存在非法、重复或本人接收人");
            }
            result.add(value.longValue());
        }
        return Collections.unmodifiableList(result);
    }
}
