package com.WorkOrder.messaging.outbox;

import java.time.LocalDateTime;

/**
 * message_outbox 的持久化模型。
 * 标准 getter/setter 用于 JDBC 行映射、事件发布和 Relay 状态处理。
 */
public class OutboxEvent {
    /** 数据库内部主键。 */
    private Long id;
    /** 全局唯一事件 ID。 */
    private String eventId;
    /** 生产该消息的服务标识。 */
    private String sourceService;
    /** 业务聚合类型。 */
    private String aggregateType;
    /** 业务聚合 ID。 */
    private String aggregateId;
    /** 聚合状态版本。 */
    private Long aggregateVersion;
    /** 稳定的领域事件类型。 */
    private String eventType;
    /** 事件契约版本。 */
    private int eventVersion;
    /** RocketMQ Topic。 */
    private String topic;
    /** RocketMQ Tag。 */
    private String tag;
    /** RocketMQ Keys，默认与 eventId 相同。 */
    private String messageKey;
    /** 完整且不可变的统一事件信封 JSON。 */
    private String payload;
    /** 当前 Outbox 状态。 */
    private OutboxStatus status;
    /** 已发生的发送失败次数。 */
    private int retryCount;
    /** RETRY 状态允许再次认领的时间。 */
    private LocalDateTime nextRetryAt;
    /** 当前持有发送锁的 Relay 实例。 */
    private String lockedBy;
    /** 当前发送锁的获取时间。 */
    private LocalDateTime lockedAt;

    /** @return 数据库内部主键 */
    public Long getId() {
        return id;
    }

    /** @param id 数据库内部主键 */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 全局唯一事件 ID */
    public String getEventId() {
        return eventId;
    }

    /** @param eventId 全局唯一事件 ID */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /** @return 生产服务标识 */
    public String getSourceService() {
        return sourceService;
    }

    /** @param sourceService 生产服务标识 */
    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    /** @return 业务聚合类型 */
    public String getAggregateType() {
        return aggregateType;
    }

    /** @param aggregateType 业务聚合类型 */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /** @return 业务聚合 ID */
    public String getAggregateId() {
        return aggregateId;
    }

    /** @param aggregateId 业务聚合 ID */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /** @return 聚合状态版本 */
    public Long getAggregateVersion() {
        return aggregateVersion;
    }

    /** @param aggregateVersion 聚合状态版本 */
    public void setAggregateVersion(Long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    /** @return 领域事件类型 */
    public String getEventType() {
        return eventType;
    }

    /** @param eventType 领域事件类型 */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /** @return 事件契约版本 */
    public int getEventVersion() {
        return eventVersion;
    }

    /** @param eventVersion 事件契约版本 */
    public void setEventVersion(int eventVersion) {
        this.eventVersion = eventVersion;
    }

    /** @return RocketMQ Topic */
    public String getTopic() {
        return topic;
    }

    /** @param topic RocketMQ Topic */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /** @return RocketMQ Tag */
    public String getTag() {
        return tag;
    }

    /** @param tag RocketMQ Tag */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /** @return RocketMQ 消息 Key */
    public String getMessageKey() {
        return messageKey;
    }

    /** @param messageKey RocketMQ 消息 Key */
    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    /** @return 完整事件信封 JSON */
    public String getPayload() {
        return payload;
    }

    /** @param payload 完整事件信封 JSON */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /** @return 当前 Outbox 状态 */
    public OutboxStatus getStatus() {
        return status;
    }

    /** @param status 当前 Outbox 状态 */
    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    /** @return 已发生的发送失败次数 */
    public int getRetryCount() {
        return retryCount;
    }

    /** @param retryCount 已发生的发送失败次数 */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /** @return 下一次允许重试的时间 */
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /** @param nextRetryAt 下一次允许重试的时间 */
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /** @return 当前持锁 Relay 实例标识 */
    public String getLockedBy() {
        return lockedBy;
    }

    /** @param lockedBy 当前持锁 Relay 实例标识 */
    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    /** @return 当前发送锁获取时间 */
    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    /** @param lockedAt 当前发送锁获取时间 */
    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
}
