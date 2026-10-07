package com.WorkOrder.messaging.outbox;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description message_outbox 的持久化模型。
 * 标准 getter/setter 用于 JDBC 行映射、事件发布和 Relay 状态处理。
 */
public class OutboxEvent {
    /**
     * 数据库内部主键。
     */
    private Long id;
    /**
     * 全局唯一事件 ID。
     */
    private String eventId;
    /**
     * 生产该消息的服务标识。
     */
    private String sourceService;
    /**
     * 业务聚合类型。
     */
    private String aggregateType;
    /**
     * 业务聚合 ID。
     */
    private String aggregateId;
    /**
     * 聚合状态版本。
     */
    private Long aggregateVersion;
    /**
     * 稳定的领域事件类型。
     */
    private String eventType;
    /**
     * 事件契约版本。
     */
    private int eventVersion;
    /**
     * RocketMQ Topic。
     */
    private String topic;
    /**
     * RocketMQ Tag。
     */
    private String tag;
    /**
     * RocketMQ Keys，默认与 eventId 相同。
     */
    private String messageKey;
    /**
     * 完整且不可变的统一事件信封 JSON。
     */
    private String payload;
    /**
     * 当前 Outbox 状态。
     */
    private OutboxStatus status;
    /**
     * 已发生的发送失败次数。
     */
    private int retryCount;
    /**
     * RETRY 状态允许再次认领的时间。
     */
    private LocalDateTime nextRetryAt;
    /**
     * 当前持有发送锁的 Relay 实例。
     */
    private String lockedBy;
    /**
     * 当前发送锁的获取时间。
     */
    private LocalDateTime lockedAt;

    /**
     * 获取数据库内部主键。
     *
     * @return 数据库内部主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据库内部主键。
     *
     * @param id 数据库内部主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取全局唯一事件 ID。
     *
     * @return 全局唯一事件 ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 设置全局唯一事件 ID。
     *
     * @param eventId 全局唯一事件 ID
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * 获取生产该消息的服务标识。
     *
     * @return 生产服务标识
     */
    public String getSourceService() {
        return sourceService;
    }

    /**
     * 设置生产该消息的服务标识。
     *
     * @param sourceService 生产服务标识
     */
    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    /**
     * 获取业务聚合类型。
     *
     * @return 业务聚合类型
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 设置业务聚合类型。
     *
     * @param aggregateType 业务聚合类型
     */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /**
     * 获取业务聚合 ID。
     *
     * @return 业务聚合 ID
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 设置业务聚合 ID。
     *
     * @param aggregateId 业务聚合 ID
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * 获取聚合状态版本。
     *
     * @return 聚合状态版本
     */
    public Long getAggregateVersion() {
        return aggregateVersion;
    }

    /**
     * 设置聚合状态版本。
     *
     * @param aggregateVersion 聚合状态版本
     */
    public void setAggregateVersion(Long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 获取稳定的领域事件类型。
     *
     * @return 领域事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 设置稳定的领域事件类型。
     *
     * @param eventType 领域事件类型
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 获取事件契约版本。
     *
     * @return 事件契约版本
     */
    public int getEventVersion() {
        return eventVersion;
    }

    /**
     * 设置事件契约版本。
     *
     * @param eventVersion 事件契约版本
     */
    public void setEventVersion(int eventVersion) {
        this.eventVersion = eventVersion;
    }

    /**
     * 获取RocketMQ Topic。
     *
     * @return RocketMQ Topic
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 设置RocketMQ Topic。
     *
     * @param topic RocketMQ Topic
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 获取RocketMQ Tag。
     *
     * @return RocketMQ Tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * 设置RocketMQ Tag。
     *
     * @param tag RocketMQ Tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * 获取RocketMQ Keys，默认与 eventId 相同。
     *
     * @return RocketMQ 消息 Key
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * 设置RocketMQ Keys，默认与 eventId 相同。
     *
     * @param messageKey RocketMQ 消息 Key
     */
    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * 获取完整且不可变的统一事件信封 JSON。
     *
     * @return 完整事件信封 JSON
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 设置完整且不可变的统一事件信封 JSON。
     *
     * @param payload 完整事件信封 JSON
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 获取当前 Outbox 状态。
     *
     * @return 当前 Outbox 状态
     */
    public OutboxStatus getStatus() {
        return status;
    }

    /**
     * 设置当前 Outbox 状态。
     *
     * @param status 当前 Outbox 状态
     */
    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    /**
     * 获取已发生的发送失败次数。
     *
     * @return 已发生的发送失败次数
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 设置已发生的发送失败次数。
     *
     * @param retryCount 已发生的发送失败次数
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 获取RETRY 状态允许再次认领的时间。
     *
     * @return 下一次允许重试的时间
     */
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /**
     * 设置RETRY 状态允许再次认领的时间。
     *
     * @param nextRetryAt 下一次允许重试的时间
     */
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * 获取当前持有发送锁的 Relay 实例。
     *
     * @return 当前持锁 Relay 实例标识
     */
    public String getLockedBy() {
        return lockedBy;
    }

    /**
     * 设置当前持有发送锁的 Relay 实例。
     *
     * @param lockedBy 当前持锁 Relay 实例标识
     */
    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    /**
     * 获取当前发送锁的获取时间。
     *
     * @return 当前发送锁获取时间
     */
    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    /**
     * 设置当前发送锁的获取时间。
     *
     * @param lockedAt 当前发送锁获取时间
     */
    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
}
