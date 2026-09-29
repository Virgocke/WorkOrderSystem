package com.WorkOrder.assignment.messaging;

import com.WorkOrder.assignment.service.AssignEngineService;
import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.ticket.contract.TicketCreatedPayload;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 工单创建后计算候选人并幂等地产生自动派单提议。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        consumerGroup = "${work-order.messaging.ticket-created.consumer-group:assign-ticket-created-v1}",
        topic = "${work-order.messaging.ticket-topic:wo-ticket-event}",
        selectorExpression = "CREATED",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16)
public class TicketCreatedAssignmentListener implements RocketMQListener<WorkOrderEvent> {
    /** 事件 ID 的固定格式。 */
    private static final Pattern EVENT_ID = Pattern.compile("[0-9a-fA-F]{32}");
    /** 服务日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketCreatedAssignmentListener.class);

    /** 消费日志和提议 Outbox 的同事务执行器。 */
    private final IdempotentConsumerExecutor executor;
    /** 现有的处理人评分算法。 */
    private final AssignEngineService assignEngineService;
    /** 自动派单提议发布器。 */
    private final AssignmentProposedEventPublisher proposalPublisher;
    /** 独立消费组。 */
    private final String consumerGroup;
    /** 工单事件 Topic。 */
    private final String topic;

    /** 注入评分、发布和幂等组件。 */
    public TicketCreatedAssignmentListener(
            IdempotentConsumerExecutor executor,
            AssignEngineService assignEngineService,
            AssignmentProposedEventPublisher proposalPublisher,
            @Value("${work-order.messaging.ticket-created.consumer-group:assign-ticket-created-v1}")
                    String consumerGroup,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.executor = executor;
        this.assignEngineService = assignEngineService;
        this.proposalPublisher = proposalPublisher;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
    }

    /** 消费创建事实；无可用候选人时工单保留待分配状态，供管理员处理。 */
    @Override
    public void onMessage(WorkOrderEvent event) {
        if (event == null || event.getEventId() == null
                || !EVENT_ID.matcher(event.getEventId()).matches()
                || !"ticket-service".equals(event.getProducer())) {
            throw new IllegalArgumentException("创建事件 ID 无效");
        }
        // 创建事件
        TicketCreatedPayload created = TicketCreatedPayload.from(event);
        // 执行消费
        executor.execute(
                consumerGroup,
                event,
                topic,
                "CREATED",
                () -> {
                    // 计算候选人
            AssignCandidate candidate = assignEngineService.recommendForSystem(created.getTicketId());
            if (candidate == null) {
                LOGGER.warn("工单 {} 暂无有容量的处理人，保留待分配状态", created.getTicketId());
                return;
            }
            // 发布提议
            proposalPublisher.publish(created.getTicketId(), event.getEventId(), candidate);
        });
    }
}
