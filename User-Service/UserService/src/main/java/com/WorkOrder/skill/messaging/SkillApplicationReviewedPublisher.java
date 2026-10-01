package com.WorkOrder.skill.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.skill.contract.SkillApplicationReviewedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/** 在审核事务中固化结果并写入 Outbox。 */
@Component
public class SkillApplicationReviewedPublisher {
    /** 当前服务的事务 Outbox 发布器，消息关闭时可为空。 */
    private final Optional<DomainEventPublisher> publisher;
    /** 查询或序列化所用组件。 */
    private final ObjectMapper mapper;
    /** 技能审核事件 Topic。 */
    private final String topic;
    /** 消息底座是否开启。 */
    private final boolean enabled;

    /** 注入发布器、JSON 组件、消息 Topic 和启用状态。 */
    public SkillApplicationReviewedPublisher(Optional<DomainEventPublisher> publisher, ObjectMapper mapper,
            @Value("${work-order.messaging.skill-topic:wo-skill-event}") String topic,
            @Value("${work-order.messaging.enabled:false}") boolean enabled) {
        this.publisher = publisher;
        this.mapper = mapper;
        this.topic = topic;
        this.enabled = enabled;
    }

    /** 消息关闭时遵循项目现有行为；启用但发布器缺失时使审核回滚。 */
    public void publish(SkillApplication application) {
        if (!publisher.isPresent()) {
            if (enabled) { throw new IllegalStateException("技能审核消息发布器未创建"); }
            return;
        }
        OffsetDateTime at = LocalDateTime.parse(application.getReviewedAt().replace(' ', 'T')).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        ObjectNode p = mapper.createObjectNode();
        p.put("applicationId", application.getId());
        p.put("applicantId", application.getHandlerId());
        p.put("skillId", application.getSkillId());
        p.put("skillName", application.getSkillName());
        p.put("type", application.getType());
        p.put("status", application.getStatus());
        p.put("reviewerId", application.getReviewerId());
        p.put("reviewComment", application.getReviewComment());
        p.put("reviewedAt", at.toString());
        WorkOrderEvent event = WorkOrderEvent.of(EventType.SKILL_APPLICATION_REVIEWED,
                "SKILL_APPLICATION", application.getId().toString(), p)
                .withActorId(application.getReviewerId().toString());
        event.setOccurredAt(at);
        SkillApplicationReviewedPayload.from(event);
        publisher.get().publish(event, topic, "REVIEWED");
    }
}
