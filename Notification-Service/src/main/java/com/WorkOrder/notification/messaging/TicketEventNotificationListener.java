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

/** 消费工单事件，幂等生成对应的站内通知。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        consumerGroup = "${work-order.messaging.ticket-events.consumer-group:notification-ticket-event-v1}",
        topic = "${work-order.messaging.ticket-topic:wo-ticket-event}",
        selectorExpression = "${work-order.messaging.ticket-events.selector-expression:ASSIGNED || REPLIED || REMINDED || TRANSFERRED}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16
)
public class TicketEventNotificationListener implements RocketMQListener<WorkOrderEvent> {

    static final String DEFAULT_CONSUMER_GROUP = "notification-ticket-event-v1";
    static final String DEFAULT_TOPIC = "wo-ticket-event";
    static final String ASSIGNED_TAG = "ASSIGNED";
    static final String REPLIED_TAG = "REPLIED";
    static final String REMINDED_TAG = "REMINDED";
    static final String TRANSFERRED_TAG = "TRANSFERRED";

    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    private final IdempotentConsumerExecutor idempotentConsumerExecutor;
    private final TicketAssignedNotificationHandler assignedHandler;
    private final TicketRepliedNotificationHandler repliedHandler;
    private final TicketRemindedNotificationHandler remindedHandler;
    private final TicketTransferredNotificationHandler transferredHandler;
    private final String consumerGroup;
    private final String topic;

    /**
     * 创建统一工单通知事件监听器。
     *
     * @param idempotentConsumerExecutor 幂等消费事务执行器
     * @param assignedHandler 派单通知处理器
     * @param repliedHandler 回复通知处理器
     * @param remindedHandler 催办通知处理器
     * @param transferredHandler 转派通知处理器
     * @param consumerGroup 通知服务消费组
     * @param topic 工单事件 Topic
     */
    public TicketEventNotificationListener(
            IdempotentConsumerExecutor idempotentConsumerExecutor,
            TicketAssignedNotificationHandler assignedHandler,
            TicketRepliedNotificationHandler repliedHandler,
            TicketRemindedNotificationHandler remindedHandler,
            TicketTransferredNotificationHandler transferredHandler,
            @Value("${work-order.messaging.ticket-events.consumer-group:notification-ticket-event-v1}")
                    String consumerGroup,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.idempotentConsumerExecutor = idempotentConsumerExecutor;
        this.assignedHandler = assignedHandler;
        this.repliedHandler = repliedHandler;
        this.remindedHandler = remindedHandler;
        this.transferredHandler = transferredHandler;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
    }

    /**
     * 校验统一事件信封，在同一本地事务中写入消费日志和业务通知。
     * 业务异常继续向上抛出，由 RocketMQ 负责重试。
     *
     * @param event 工单领域事件
     */
    @Override
    public void onMessage(WorkOrderEvent event) {
        validateEnvelope(event);
        String tag = resolveTag(event);
        idempotentConsumerExecutor.execute(
                consumerGroup,
                event,
                topic,
                tag,
                () -> dispatch(event)
        );
    }

    /**
     * 根据事件类型调用对应的通知处理器。
     *
     * @param event 已通过信封校验的工单事件
     */
    private void dispatch(WorkOrderEvent event) {
        if (EventType.TICKET_ASSIGNED.name().equals(event.getEventType())) {
            assignedHandler.handle(event);
            return;
        }
        if (EventType.TICKET_REPLIED.name().equals(event.getEventType())) {
            repliedHandler.handle(event);
            return;
        }
        if (EventType.TICKET_REMINDED.name().equals(event.getEventType())) {
            remindedHandler.handle(event);
            return;
        }
        if (EventType.TICKET_TRANSFERRED.name().equals(event.getEventType())) {
            transferredHandler.handle(event);
            return;
        }
        throw new IllegalArgumentException("不支持的工单通知事件：" + event.getEventType());
    }

    /**
     * 将领域事件类型映射为实际订阅的 RocketMQ Tag。
     *
     * @param event 工单事件
     * @return RocketMQ Tag
     */
    private String resolveTag(WorkOrderEvent event) {
        if (EventType.TICKET_ASSIGNED.name().equals(event.getEventType())) {
            return ASSIGNED_TAG;
        }
        if (EventType.TICKET_REPLIED.name().equals(event.getEventType())) {
            return REPLIED_TAG;
        }
        if (EventType.TICKET_REMINDED.name().equals(event.getEventType())) {
            return REMINDED_TAG;
        }
        if (EventType.TICKET_TRANSFERRED.name().equals(event.getEventType())) {
            return TRANSFERRED_TAG;
        }
        throw new IllegalArgumentException("不支持的工单通知事件：" + event.getEventType());
    }

    /**
     * 校验统一事件信封及本监听器支持的事件类型。
     *
     * @param event 待消费事件
     */
    private void validateEnvelope(WorkOrderEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("工单事件不能为空");
        }
        if (event.getEventId() == null
                || !EVENT_ID_PATTERN.matcher(event.getEventId()).matches()) {
            throw new IllegalArgumentException("工单事件 eventId 必须是 32 位十六进制字符");
        }
        resolveTag(event);
        if (event.getEventVersion() != EventVersion.V1) {
            throw new IllegalArgumentException("不支持的工单事件版本：" + event.getEventVersion());
        }
        if (!"TICKET".equals(event.getAggregateType()) || isBlank(event.getAggregateId())) {
            throw new IllegalArgumentException("工单事件的聚合标识无效");
        }
        if (event.getOccurredAt() == null || isBlank(event.getProducer())
                || isBlank(event.getActorId()) || event.getPayload() == null
                || !event.getPayload().isObject()) {
            throw new IllegalArgumentException("工单事件信封缺少必填字段");
        }
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待检查字符串
     * @return 为空或仅含空白字符时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
