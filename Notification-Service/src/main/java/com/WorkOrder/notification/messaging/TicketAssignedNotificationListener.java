package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.EventVersion;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 消费工单派单事件，幂等生成处理人站内通知。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        consumerGroup = "${work-order.messaging.ticket-assigned.consumer-group:notification-ticket-event-v1}",
        topic = "${work-order.messaging.ticket-topic:wo-ticket-event}",
        selectorExpression = "${work-order.messaging.ticket-assigned.selector-expression:ASSIGNED}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16
)
public class TicketAssignedNotificationListener implements RocketMQListener<WorkOrderEvent> {

    static final String DEFAULT_CONSUMER_GROUP = "notification-ticket-event-v1";
    static final String DEFAULT_TOPIC = "wo-ticket-event";
    static final String DEFAULT_TAG = "ASSIGNED";

    // eventId 校验正则
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    // 消费者执行器
    private final IdempotentConsumerExecutor idempotentConsumerExecutor;
    // 派单通知处理器
    private final TicketAssignedNotificationHandler notificationHandler;
    // 消费组
    private final String consumerGroup;
    // 工单事件 Topic
    private final String topic;
    // 派单事件 Tag
    private final String tag;

    /**
     * 创建派单事件监听器。
     *
     * @param idempotentConsumerExecutor 幂等消费事务执行器
     * @param notificationHandler 派单通知处理器
     * @param consumerGroup 消费组
     * @param topic 工单事件 Topic
     * @param tag 派单事件 Tag
     */
    public TicketAssignedNotificationListener(
            IdempotentConsumerExecutor idempotentConsumerExecutor,
            TicketAssignedNotificationHandler notificationHandler,
            @Value("${work-order.messaging.ticket-assigned.consumer-group:notification-ticket-event-v1}")
                    String consumerGroup,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic,
            @Value("${work-order.messaging.ticket-assigned.selector-expression:ASSIGNED}") String tag) {
        this.idempotentConsumerExecutor = idempotentConsumerExecutor;
        this.notificationHandler = notificationHandler;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.tag = tag;
    }

    /**
     * 校验统一事件信封，在同一本地事务中写入消费日志和通知记录。
     * 任何业务异常都会继续向上抛出，交由 RocketMQ 重试。
     *
     * @param event 工单派单事件
     */
    @Override
    public void onMessage(WorkOrderEvent event) {
        validateEnvelope(event);
        idempotentConsumerExecutor.execute(
                consumerGroup,
                event,
                topic,
                tag,
                () -> notificationHandler.handle(event)
        );
    }

    private void validateEnvelope(WorkOrderEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("派单事件不能为空");
        }
        if (event.getEventId() == null
                || !EVENT_ID_PATTERN.matcher(event.getEventId()).matches()) {
            throw new IllegalArgumentException("派单事件 eventId 必须是 32 位十六进制字符");
        }
        if (!EventType.TICKET_ASSIGNED.name().equals(event.getEventType())) {
            throw new IllegalArgumentException("仅支持 TICKET_ASSIGNED 事件");
        }
        if (event.getEventVersion() != EventVersion.V1) {
            throw new IllegalArgumentException("不支持的派单事件版本: " + event.getEventVersion());
        }
        if (!"TICKET".equals(event.getAggregateType()) || isBlank(event.getAggregateId())) {
            throw new IllegalArgumentException("派单事件的工单聚合标识无效");
        }
        if (event.getOccurredAt() == null || isBlank(event.getProducer())
                || isBlank(event.getActorId()) || event.getPayload() == null
                || !event.getPayload().isObject()) {
            throw new IllegalArgumentException("派单事件信封缺少必填字段");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
