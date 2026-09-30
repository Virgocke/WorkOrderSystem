package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.ticket.contract.EscalationRequestedPayload;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** SLA 请求的独立消费组，消费日志、工单变更和最终事件共用事务。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        consumerGroup = "${work-order.messaging.escalation-requested.consumer-group:ticket-escalation-requested-v1}",
        topic = "${work-order.messaging.ticket-topic:wo-ticket-event}",
        selectorExpression = "ESCALATION_REQUESTED", consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16)
public class EscalationRequestedTicketListener implements RocketMQListener<WorkOrderEvent> {
    /** 同事务幂等执行器。 */
    private final IdempotentConsumerExecutor executor;
    /** 自动升级事务处理器。 */
    private final SystemTicketEscalationHandler handler;
    /** 独立消费组。 */
    private final String group;
    /** 事件主题。 */
    private final String topic;

    /** 注入事务处理器及消息路由。 */
    public EscalationRequestedTicketListener(IdempotentConsumerExecutor executor,
            SystemTicketEscalationHandler handler,
            @Value("${work-order.messaging.escalation-requested.consumer-group:ticket-escalation-requested-v1}") String group,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.executor = executor;
        this.handler = handler;
        this.group = group;
        this.topic = topic;
    }

    /** 拒绝非 SLA 服务请求；重复事件或重复扫描都不会重复升级。 */
    @Override
    public void onMessage(WorkOrderEvent event) {
        if (event == null || event.getEventId() == null || !event.getEventId().matches("[0-9a-fA-F]{32}")
                || !"sla-monitor".equals(event.getProducer())) {
            throw new IllegalArgumentException("自动升级请求来源或事件ID无效");
        }
        long ticketId = EscalationRequestedPayload.ticketId(event);
        executor.execute(group, event, topic, "ESCALATION_REQUESTED", () -> handler.confirm(ticketId));
    }
}
