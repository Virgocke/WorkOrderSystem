package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;

/** 组装并发布工单催办事件。 */
@Component
public class TicketRemindedEventPublisher {

    static final String DEFAULT_TOPIC = "wo-ticket-event";
    static final String TAG = "REMINDED";

    private final Optional<DomainEventPublisher> domainEventPublisher;
    private final String topic;
    private final boolean messagingEnabled;
    private final ObjectMapper objectMapper;

    /**
     * 创建工单催办事件发布器。
     *
     * @param domainEventPublisher 事务性 Outbox 发布器
     * @param topic 工单事件 Topic
     * @param messagingEnabled 是否启用消息底座
     * @param objectMapper JSON 序列化组件
     */
    public TicketRemindedEventPublisher(
            Optional<DomainEventPublisher> domainEventPublisher,
            @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic,
            @Value("${work-order.messaging.enabled:false}") boolean messagingEnabled,
            ObjectMapper objectMapper) {
        this.domainEventPublisher = domainEventPublisher;
        this.topic = topic;
        this.messagingEnabled = messagingEnabled;
        this.objectMapper = objectMapper;
    }

    /**
     * 将催办事实及接收人快照写入当前业务事务的 Outbox。
     * 催办次数只是业务字段，不作为聚合版本使用。
     *
     * @param ticket 催办次数已更新的工单
     * @param remindLog 已持久化并取得主键的催办日志
     * @param remindCount 更新后的催办次数
     * @param remindedAt 催办发生时间
     * @param receiverIds 当前规则计算出的通知接收人快照
     */
    public void publish(Tickets ticket,
                        TicketOperationLog remindLog,
                        int remindCount,
                        LocalDateTime remindedAt,
                        Collection<Long> receiverIds) {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null) {
            if (messagingEnabled) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }

        ZonedDateTime occurredAt = remindedAt.atZone(ZoneId.systemDefault());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("remindLogId", remindLog.getId());
        payload.put("remindCount", remindCount);
        payload.put("remindedBy", remindLog.getOperatorId());
        payload.put("remindedAt", occurredAt.toOffsetDateTime().toString());
        if (ticket.getHandlerId() == null) {
            payload.putNull("handlerId");
        } else {
            payload.put("handlerId", ticket.getHandlerId());
        }
        ArrayNode receivers = payload.putArray("receiverIds");
        for (Long receiverId : receiverIds) {
            receivers.add(receiverId);
        }

        WorkOrderEvent event = WorkOrderEvent.of(
                EventType.TICKET_REMINDED,
                "TICKET",
                String.valueOf(ticket.getId()),
                payload
        ).withActorId(String.valueOf(remindLog.getOperatorId()));
        event.setOccurredAt(occurredAt.toOffsetDateTime());
        publisher.publish(event, topic, TAG);
    }
}
