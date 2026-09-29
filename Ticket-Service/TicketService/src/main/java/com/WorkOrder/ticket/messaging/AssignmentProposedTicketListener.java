package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.ticket.contract.AssignmentProposedPayload;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 消费自动派单提议，并在工单服务中确认最终分配。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        consumerGroup = "${work-order.messaging.assignment-proposed.consumer-group:ticket-assignment-proposed-v1}",
        topic = "${work-order.messaging.ticket-topic:wo-ticket-event}",
        selectorExpression = "ASSIGNMENT_PROPOSED",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16)
public class AssignmentProposedTicketListener implements RocketMQListener<WorkOrderEvent> {
    /** 事件 ID 的固定格式。 */
    private static final Pattern EVENT_ID = Pattern.compile("[0-9a-fA-F]{32}");
    /** 同事务幂等执行器。 */
    private final IdempotentConsumerExecutor executor;
    /** 系统派单事务处理器。 */
    private final SystemTicketAssignmentHandler assignmentHandler;
    /** 提议消费组。 */
    private final String consumerGroup;
    /** 当前工单事件 Topic。 */
    private final String topic;

    /** 注入幂等执行器、派单事务和消息路由配置。 */
    public AssignmentProposedTicketListener(
            IdempotentConsumerExecutor executor,
            SystemTicketAssignmentHandler assignmentHandler,
            @Value("${work-order.messaging.assignment-proposed.consumer-group:ticket-assignment-proposed-v1}")
                    String consumerGroup,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.executor = executor;
        this.assignmentHandler = assignmentHandler;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
    }

    /** 重复事件由消费日志拦截；过期提议由状态条件更新跳过。 */
    @Override
    public void onMessage(WorkOrderEvent event) {
        if (event == null || event.getEventId() == null
                || !EVENT_ID.matcher(event.getEventId()).matches()
                || !"assign-engine".equals(event.getProducer())) {
            throw new IllegalArgumentException("自动派单提议事件 ID 无效");
        }
        AssignmentProposedPayload proposal = AssignmentProposedPayload.from(event);
        executor.execute(
                consumerGroup,
                event,
                topic,
                "ASSIGNMENT_PROPOSED",
                () -> assignmentHandler.confirm(proposal));
    }
}
