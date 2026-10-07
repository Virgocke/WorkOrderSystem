package com.WorkOrder.messaging.contract;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 与具体业务实体解耦的统一事件信封。
 * 标准 getter/setter 供 Jackson 反序列化及各业务服务组装事件使用。
 */
public class WorkOrderEvent {
    /**
     * 全局唯一事件 ID，也是生产端和消费端的业务幂等键。
     */
    private String eventId;
    /**
     * 稳定的事件类型名称，不使用 Java 类全限定名。
     */
    private String eventType;
    /**
     * 事件 JSON 契约版本。
     */
    private int eventVersion = EventVersion.V1;
    /**
     * 业务聚合类型，例如 TICKET。
     */
    private String aggregateType;
    /**
     * 业务聚合 ID，工单事件通常为 ticketId。
     */
    private String aggregateId;
    /**
     * 聚合状态版本，用于消费者识别重复、陈旧和跳号事件。
     */
    private Long aggregateVersion;
    /**
     * 业务事件实际发生时间，包含时区。
     */
    private OffsetDateTime occurredAt;
    /**
     * 生产事件的服务标识。
     */
    private String producer;
    /**
     * 跨服务链路追踪标识。
     */
    private String traceId;
    /**
     * 触发业务动作的用户 ID。
     */
    private String actorId;
    /**
     * 消费者完成业务动作所需的稳定数据快照。
     */
    private JsonNode payload;

    /**
     * 创建一个包含必需业务字段的 V1 事件信封。
     * eventId、发生时间和生产服务由发布器在持久化前补齐。
     *
     * @param eventType 领域事件类型
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合 ID
     * @param payload 业务数据快照
     * @return 待发布的统一事件信封
     */
    public static WorkOrderEvent of(EventType eventType, String aggregateType,
                                    String aggregateId, JsonNode payload) {
        WorkOrderEvent event = new WorkOrderEvent();
        event.setEventType(eventType.name());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        return event;
    }

    /**
     * 设置聚合状态版本。
     *
     * @param version 聚合更新后的版本
     * @return 当前事件对象，便于链式组装
     */
    public WorkOrderEvent withAggregateVersion(Long version) {
        this.aggregateVersion = version;
        return this;
    }

    /**
     * 设置触发事件的操作人。
     *
     * @param actorId 操作人 ID
     * @return 当前事件对象，便于链式组装
     */
    public WorkOrderEvent withActorId(String actorId) {
        this.actorId = actorId;
        return this;
    }

    /**
     * 设置链路追踪标识。
     *
     * @param traceId 链路追踪 ID
     * @return 当前事件对象，便于链式组装
     */
    public WorkOrderEvent withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * 获取全局唯一事件 ID，也是生产端和消费端的业务幂等键。
     *
     * @return 全局唯一事件 ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 设置全局唯一事件 ID，也是生产端和消费端的业务幂等键。
     *
     * @param eventId 全局唯一事件 ID
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * 获取稳定的事件类型名称，不使用 Java 类全限定名。
     *
     * @return 稳定的事件类型名称
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 设置稳定的事件类型名称，不使用 Java 类全限定名。
     *
     * @param eventType 稳定的事件类型名称
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 获取事件 JSON 契约版本。
     *
     * @return 事件契约版本
     */
    public int getEventVersion() {
        return eventVersion;
    }

    /**
     * 设置事件 JSON 契约版本。
     *
     * @param eventVersion 事件契约版本
     */
    public void setEventVersion(int eventVersion) {
        this.eventVersion = eventVersion;
    }

    /**
     * 获取业务聚合类型，例如 TICKET。
     *
     * @return 业务聚合类型
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 设置业务聚合类型，例如 TICKET。
     *
     * @param aggregateType 业务聚合类型
     */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /**
     * 获取业务聚合 ID，工单事件通常为 ticketId。
     *
     * @return 业务聚合 ID
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 设置业务聚合 ID，工单事件通常为 ticketId。
     *
     * @param aggregateId 业务聚合 ID
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * 获取聚合状态版本，用于消费者识别重复、陈旧和跳号事件。
     *
     * @return 聚合状态版本
     */
    public Long getAggregateVersion() {
        return aggregateVersion;
    }

    /**
     * 设置聚合状态版本，用于消费者识别重复、陈旧和跳号事件。
     *
     * @param aggregateVersion 聚合状态版本
     */
    public void setAggregateVersion(Long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 获取业务事件实际发生时间，包含时区。
     *
     * @return 业务事件发生时间
     */
    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    /**
     * 设置业务事件实际发生时间，包含时区。
     *
     * @param occurredAt 业务事件发生时间
     */
    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    /**
     * 获取生产事件的服务标识。
     *
     * @return 生产服务标识
     */
    public String getProducer() {
        return producer;
    }

    /**
     * 设置生产事件的服务标识。
     *
     * @param producer 生产服务标识
     */
    public void setProducer(String producer) {
        this.producer = producer;
    }

    /**
     * 获取跨服务链路追踪标识。
     *
     * @return 链路追踪 ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 设置跨服务链路追踪标识。
     *
     * @param traceId 链路追踪 ID
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 获取触发业务动作的用户 ID。
     *
     * @return 触发事件的操作人 ID
     */
    public String getActorId() {
        return actorId;
    }

    /**
     * 设置触发业务动作的用户 ID。
     *
     * @param actorId 触发事件的操作人 ID
     */
    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    /**
     * 获取消费者完成业务动作所需的稳定数据快照。
     *
     * @return 事件业务数据快照
     */
    public JsonNode getPayload() {
        return payload;
    }

    /**
     * 设置消费者完成业务动作所需的稳定数据快照。
     *
     * @param payload 事件业务数据快照
     */
    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
