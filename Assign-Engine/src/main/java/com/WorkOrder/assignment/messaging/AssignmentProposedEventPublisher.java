package com.WorkOrder.assignment.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.ticket.contract.AssignmentProposedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 在创建事件消费事务内发布自动派单提议。 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
public class AssignmentProposedEventPublisher {
    /** 事务性 Outbox 发布器。 */
    private final DomainEventPublisher domainEventPublisher;
    /** JSON 载荷构建器。 */
    private final ObjectMapper objectMapper;
    /** 当前环境的工单事件 Topic。 */
    private final String topic;

    /** 注入发布器和消息 Topic。 */
    public AssignmentProposedEventPublisher(
            DomainEventPublisher domainEventPublisher,
            ObjectMapper objectMapper,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    /**
     * 将推荐时的候选人与评分作为不可变快照写入 Outbox。
     *
     * @param ticketId 新建工单 ID
     * @param createdEventId 来源创建事件 ID
     * @param candidate 最高分且仍有容量的候选人
     */
    public void publish(long ticketId, String createdEventId, AssignCandidate candidate) {
        OffsetDateTime proposedAt = OffsetDateTime.now(ZoneId.systemDefault());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("createdEventId", createdEventId);
        payload.put("ticketId", ticketId);
        payload.put("handlerId", candidate.getHandler().getUserId());
        payload.put("score", candidate.getTotalScore());
        payload.put("skillMatchScore", candidate.getSkillMatchScore());
        payload.put("loadScore", candidate.getLoadScore());
        payload.put("slaScore", candidate.getSlaScore());
        payload.put("ratingScore", candidate.getRatingScore());
        payload.put("proposedAt", proposedAt.toString());

        WorkOrderEvent proposal = WorkOrderEvent.of(EventType.ASSIGNMENT_PROPOSED,
                "TICKET", String.valueOf(ticketId), payload)
                .withActorId("SYSTEM");

        proposal.setOccurredAt(proposedAt);
        // 验证载荷
        AssignmentProposedPayload.from(proposal);
        // 发布事件
        domainEventPublisher.publish(proposal, topic, "ASSIGNMENT_PROPOSED");
    }
}
