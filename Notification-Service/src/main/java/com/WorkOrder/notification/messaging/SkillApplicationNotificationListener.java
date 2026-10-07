package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.skill.contract.SkillApplicationReviewedPayload;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 技能审核独立订阅，通知和消费日志同事务提交。
 */
@Component
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@RocketMQMessageListener(consumerGroup = "${work-order.messaging.skill-consumer-group:notification-skill-review-v1}",
        topic = "${work-order.messaging.skill-topic:wo-skill-event}", selectorExpression = "REVIEWED",
        maxReconsumeTimes = 16)
public class SkillApplicationNotificationListener implements RocketMQListener<WorkOrderEvent> {
    /**
     * 将消费记录与通知写入同一事务的幂等执行器。
     */
    private final IdempotentConsumerExecutor executor;
    /**
     * 站内信与邮件统一分发服务。
     */
    private final NotificationDeliveryService delivery;
    /**
     * 独立的技能审核消费组。
     */
    private final String group;
    /**
     * 技能审核事件 Topic。
     */
    private final String topic;

    /**
     * 注入幂等执行器、统一分发服务及独立订阅配置。
     *
     * @param executor 将消费记录与通知写入同一事务的幂等执行器
     * @param delivery 站内信与邮件统一分发服务
     * @param group 独立的技能审核消费组
     * @param topic 技能审核事件 Topic
     */
    public SkillApplicationNotificationListener(IdempotentConsumerExecutor executor, NotificationDeliveryService delivery,
            @Value("${work-order.messaging.skill-consumer-group:notification-skill-review-v1}") String group,
            @Value("${work-order.messaging.skill-topic:wo-skill-event}") String topic) {
        this.executor = executor;
        this.delivery = delivery;
        this.group = group;
        this.topic = topic;
    }

    /**
     * 校验结果快照后幂等通知原申请人；异常交给 RocketMQ 重试。
     *
     * @param event 技能申请审核完成事件，包含原申请人及审核结果快照
     */
    @Override
    public void onMessage(WorkOrderEvent event) {
        JsonNode p = SkillApplicationReviewedPayload.from(event);
        if (event.getEventId() == null || !event.getEventId().matches("[0-9a-fA-F]{32}")
                || event.getProducer() == null || event.getProducer().trim().isEmpty()) {
            throw new IllegalArgumentException("技能审核事件缺少来源或幂等标识");
        }
        executor.execute(
                group,
                event,
                topic,
                "REVIEWED",
                () -> {
                    // 处理技能审核事件
            String type = "ADD".equals(p.path("type").asText()) ? "新增"
                    : "REMOVE".equals(p.path("type").asText()) ? "移除" : "调整熟练度";
            String result = "APPROVED".equals(p.path("status").asText()) ? "已通过" : "已驳回";
            String comment = p.path("reviewComment").isNull() ? "未填写" : p.path("reviewComment").asText();
            Notifications n = new Notifications();
            n.setSourceEventId(event.getEventId());
            n.setSkillApplicationId(p.path("applicationId").longValue());
            n.setReceiverId(p.path("applicantId").longValue());
            n.setChannel("INTERNAL");
            n.setStatus("SENT");
            n.setSentAt(LocalDateTime.now());
            n.setContent("技能申请 #" + p.path("applicationId").asText() + "（" + type + "「"
                    + p.path("skillName").asText() + "」）" + result + "。审核意见：" + comment
                    + "。审核时间：" + p.path("reviewedAt").asText());
            delivery.deliver(n);
        });
    }
}
