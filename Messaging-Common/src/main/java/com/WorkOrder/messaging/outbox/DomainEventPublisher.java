package com.WorkOrder.messaging.outbox;

import com.WorkOrder.messaging.config.MessagingProperties;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 在当前业务事务中把领域事件持久化到 Outbox。 */
public class DomainEventPublisher {
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;

    /**
     * 创建领域事件发布器。
     *
     * @param outboxMapper Outbox 持久化组件
     * @param objectMapper 统一事件信封序列化组件
     * @param properties 当前服务的消息配置
     */
    public DomainEventPublisher(OutboxMapper outboxMapper, ObjectMapper objectMapper,
                                MessagingProperties properties) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 在当前业务事务中保存一条待发送领域事件。
     * 该方法只写入 Outbox，不访问 RocketMQ；序列化或插入失败会向上抛出，
     * 从而使调用方的业务事务一并回滚。
     *
     * @param event 待发布的统一事件信封
     * @param topic 目标 RocketMQ Topic
     * @param tag 目标 RocketMQ Tag
     * @return 本次事件稳定且全局唯一的 eventId
     * @throws IllegalStateException 当前没有活动事务或生产服务配置缺失时抛出
     * @throws IllegalArgumentException 事件字段不完整或序列化失败时抛出
     */
    public String publish(WorkOrderEvent event, String topic, String tag) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("领域事件必须在活动的本地事务中发布");
        }
        validate(event, topic, tag);
        if (isBlank(event.getEventId())) {
            event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(OffsetDateTime.now(ZoneId.systemDefault()));
        }
        if (isBlank(event.getProducer())) {
            event.setProducer(properties.getOutbox().getSourceService());
        }

        OutboxEvent outboxEvent = toOutboxEvent(event, topic, tag);
        outboxMapper.insert(outboxEvent);
        return event.getEventId();
    }

    /**
     * 将领域事件转换为可持久化的 Outbox 记录，并固化完整 JSON 快照。
     *
     * @param event 已补齐公共字段的领域事件
     * @param topic 目标 Topic
     * @param tag 目标 Tag
     * @return 状态为 NEW 的 Outbox 记录
     */
    private OutboxEvent toOutboxEvent(WorkOrderEvent event, String topic, String tag) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventId(event.getEventId());
        outboxEvent.setSourceService(properties.getOutbox().getSourceService());
        outboxEvent.setAggregateType(event.getAggregateType());
        outboxEvent.setAggregateId(event.getAggregateId());
        outboxEvent.setAggregateVersion(event.getAggregateVersion());
        outboxEvent.setEventType(event.getEventType());
        outboxEvent.setEventVersion(event.getEventVersion());
        outboxEvent.setTopic(topic);
        outboxEvent.setTag(tag);
        outboxEvent.setMessageKey(event.getEventId());
        outboxEvent.setStatus(OutboxStatus.NEW);
        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("领域事件序列化失败", exception);
        }
        return outboxEvent;
    }

    /**
     * 校验事件契约和路由信息是否满足持久化要求。
     *
     * @param event 领域事件
     * @param topic 目标 Topic
     * @param tag 目标 Tag
     */
    private void validate(WorkOrderEvent event, String topic, String tag) {
        if (event == null || isBlank(event.getEventType()) || isBlank(event.getAggregateType())
                || isBlank(event.getAggregateId()) || event.getPayload() == null) {
            throw new IllegalArgumentException("事件类型、聚合类型、聚合ID和payload不能为空");
        }
        if (event.getEventVersion() < 1) {
            throw new IllegalArgumentException("eventVersion 必须大于等于 1");
        }
        if (isBlank(topic) || isBlank(tag)) {
            throw new IllegalArgumentException("Topic 和 Tag 不能为空");
        }
        if (isBlank(properties.getOutbox().getSourceService())) {
            throw new IllegalStateException("work-order.messaging.outbox.source-service 未配置");
        }
    }

    /**
     * 判断字符串是否为空或只包含空白字符。
     *
     * @param value 待判断字符串
     * @return 为空时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
