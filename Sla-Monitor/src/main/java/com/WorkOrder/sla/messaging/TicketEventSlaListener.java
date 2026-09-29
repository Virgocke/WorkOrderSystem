package com.WorkOrder.sla.messaging;

import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.sla.mapper.SlaRecordMapper;
import com.WorkOrder.ticket.contract.TicketEscalatedPayload;
import com.WorkOrder.ticket.contract.TicketCreatedPayload;
import com.WorkOrder.ticket.contract.TicketClosedPayload;
import com.WorkOrder.ticket.contract.TicketCancelledPayload;
import com.WorkOrder.ticket.contract.TicketResolvedPayload;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 在同一 SLA 消费组内处理工单创建、升级、解决及终态事实。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        consumerGroup = "${work-order.messaging.sla-ticket-events.consumer-group:sla-ticket-event-v1}",
        topic = "${work-order.messaging.ticket-topic:wo-ticket-event}",
        selectorExpression = "CREATED || ESCALATED || RESOLVED || CLOSED || CANCELLED",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16
)
public class TicketEventSlaListener implements RocketMQListener<WorkOrderEvent> {
    /** 统一事件 ID 的 32 位十六进制格式。 */
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    /** 同事务写入消费日志与 SLA 记录的执行器。 */
    private final IdempotentConsumerExecutor executor;
    /** SLA 记录的幂等更新入口。 */
    private final SlaRecordMapper slaRecordMapper;
    /** SLA 服务独立的工单事件消费组。 */
    private final String consumerGroup;
    /** 当前工单事件 Topic。 */
    private final String topic;

    /** 创建同组消费升级、解决与终态事件的监听器。 */
    public TicketEventSlaListener(
            IdempotentConsumerExecutor executor,
            SlaRecordMapper slaRecordMapper,
            @Value("${work-order.messaging.sla-ticket-events.consumer-group:sla-ticket-event-v1}")
                    String consumerGroup,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.executor = executor;
        this.slaRecordMapper = slaRecordMapper;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
    }

    /** 校验事件并按类型更新 SLA 记录；失败时由 Broker 重试。 */
    @Override
    public void onMessage(WorkOrderEvent event) {
        if (event == null || event.getEventId() == null
                || !EVENT_ID_PATTERN.matcher(event.getEventId()).matches()
                || event.getProducer() == null || event.getProducer().trim().isEmpty()) {
            throw new IllegalArgumentException("SLA 事件信封缺少有效的事件 ID 或生产服务");
        }
        if (EventType.TICKET_CREATED.name().equals(event.getEventType())) {
            if (!"ticket-service".equals(event.getProducer())) {
                throw new IllegalArgumentException("创建事件生产服务无效");
            }
            TicketCreatedPayload payload = TicketCreatedPayload.from(event);
            executor.execute(consumerGroup, event, topic, "CREATED", () ->
                    slaRecordMapper.initializeFromCreation(payload.getTicketId(),
                            payload.getResponseDeadline().toLocalDateTime(),
                            payload.getResolutionDeadline().toLocalDateTime()));
            return;
        }
        if (EventType.TICKET_ESCALATED.name().equals(event.getEventType())) {
            TicketEscalatedPayload payload = TicketEscalatedPayload.from(event);
            executor.execute(consumerGroup, event, topic, "ESCALATED", () ->
                    slaRecordMapper.upsertEscalation(payload.getTicketId(),
                            payload.getResponseDeadline(), payload.getResolutionDeadline(),
                            payload.getEscalationLevel()));
            return;
        }
        if (EventType.TICKET_RESOLVED.name().equals(event.getEventType())) {
            TicketResolvedPayload payload = TicketResolvedPayload.from(event);
            executor.execute(consumerGroup, event, topic, "RESOLVED", () ->
                    slaRecordMapper.upsertResolution(payload.getTicketId(),
                            payload.getResponseDeadline().toLocalDateTime(),
                            payload.getResolutionDeadline().toLocalDateTime(),
                            payload.getResolvedAt().toLocalDateTime()));
            return;
        }
        if (EventType.TICKET_CLOSED.name().equals(event.getEventType())) {
            TicketClosedPayload payload = TicketClosedPayload.from(event);
            executor.execute(consumerGroup, event, topic, "CLOSED", () ->
                    slaRecordMapper.upsertTerminal(payload.getTicketId(),
                            payload.getResponseDeadline().toLocalDateTime(),
                            payload.getResolutionDeadline().toLocalDateTime(),
                            "CLOSED", payload.getClosedAt().toLocalDateTime()));
            return;
        }
        if (EventType.TICKET_CANCELLED.name().equals(event.getEventType())) {
            TicketCancelledPayload payload = TicketCancelledPayload.from(event);
            executor.execute(consumerGroup, event, topic, "CANCELLED", () ->
                    slaRecordMapper.upsertTerminal(payload.getTicketId(),
                            payload.getResponseDeadline().toLocalDateTime(),
                            payload.getResolutionDeadline().toLocalDateTime(),
                            "CANCELLED", payload.getCancelledAt().toLocalDateTime()));
            return;
        }
        throw new IllegalArgumentException("不支持的 SLA 工单事件：" + event.getEventType());
    }
}
