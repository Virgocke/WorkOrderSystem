package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketCreatedPayload;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/** 将工单创建事实写入与工单数据同一事务的 Outbox。 */
@Component
public class TicketCreatedEventPublisher {
    /** 创建事件使用的 RocketMQ Tag。 */
    public static final String TAG = "CREATED";

    /** 事务性 Outbox 发布器；消息关闭时不存在。 */
    private final Optional<DomainEventPublisher> domainEventPublisher;
    /** JSON 载荷构建器。 */
    private final ObjectMapper objectMapper;
    /** 当前环境的工单事件 Topic。 */
    private final String topic;
    /** 是否启用消息底座。 */
    private final boolean messagingEnabled;

    /** 注入消息底座及当前环境的 Topic 配置。 */
    public TicketCreatedEventPublisher(
            Optional<DomainEventPublisher> domainEventPublisher,
            ObjectMapper objectMapper,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic,
            @Value("${work-order.messaging.enabled:false}") boolean messagingEnabled) {
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.messagingEnabled = messagingEnabled;
    }

    /**
     * 写入创建事件，供自动派单和 SLA 初始化独立消费。
     *
     * @param ticket 已持久化并取得主键的工单
     * @param createdAt 本次创建工单的业务时间
     */
    public void publish(Tickets ticket, LocalDateTime createdAt) {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null) {
            if (messagingEnabled) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }
        ZonedDateTime occurredAt = createdAt.atZone(ZoneId.systemDefault());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("categoryId", ticket.getCategoryId());
        payload.put("priority", ticket.getPriority());
        payload.put("creatorId", ticket.getCreatorId());
        payload.put("status", ticket.getStatus());
        payload.put("createdAt", occurredAt.toOffsetDateTime().toString());
        payload.put("responseDeadline", ticket.getResponseDeadline().atZone(
                ZoneId.systemDefault()).toOffsetDateTime().toString());
        payload.put("resolutionDeadline", ticket.getResolutionDeadline().atZone(
                ZoneId.systemDefault()).toOffsetDateTime().toString());

        WorkOrderEvent event = WorkOrderEvent.of(
                EventType.TICKET_CREATED,
                        "TICKET",
                String.valueOf(ticket.getId()),
                        payload)
                .withActorId(String.valueOf(ticket.getCreatorId()));

        event.setOccurredAt(occurredAt.toOffsetDateTime());
        // 校验事件参数是否合法
        TicketCreatedPayload.from(event);
        publisher.publish(event, topic, TAG);
    }
}
