package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketEscalatedPayload;
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

/** 组装并发布工单升级事实。 */
@Component
public class TicketEscalatedEventPublisher {
    /** 默认工单事件 Topic。 */
    static final String DEFAULT_TOPIC = "wo-ticket-event";
    /** 升级事件标签。 */
    static final String TAG = "ESCALATED";

    /** 与工单事务共用数据库事务的 Outbox 发布器。 */
    private final Optional<DomainEventPublisher> domainEventPublisher;
    /** 当前工单事件 Topic。 */
    private final String topic;
    /** 消息底座是否启用。 */
    private final boolean messagingEnabled;
    /** 事件载荷构建器。 */
    private final ObjectMapper objectMapper;

    /** 创建事务性升级事件发布器。 */
    public TicketEscalatedEventPublisher(
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
     * 在工单升级事务中写入事件 Outbox；业务更新或事件写入失败时一起回滚。
     *
     * @param ticket 已更新升级级别的工单
     * @param fromLevel 更新前的升级级别
     * @param reason 升级原因
     * @param escalatedAt 升级发生时间
     * @param escalationLog 已持久化的升级日志
     * @param receiverIds 事件发生时的接收人快照
     */
    public void publish(Tickets ticket, int fromLevel, String reason,
                        LocalDateTime escalatedAt, TicketOperationLog escalationLog,
                        Collection<Long> receiverIds) {
        publishEvent(ticket, fromLevel, reason, escalatedAt, escalationLog, receiverIds, false);
    }

    /** 发布系统升级，允许跳到最高满足级别；系统操作人为空，接收人可包含当前处理人。 */
    public void publishSystem(Tickets ticket, int fromLevel, String reason,
                              LocalDateTime escalatedAt, TicketOperationLog escalationLog,
                              Collection<Long> receiverIds) {
        publishEvent(ticket, fromLevel, reason, escalatedAt, escalationLog, receiverIds, true);
    }

    /** 人工与自动升级共用事件组装和事务性 Outbox 写入。 */
    private void publishEvent(Tickets ticket, int fromLevel, String reason,
                              LocalDateTime escalatedAt, TicketOperationLog escalationLog,
                              Collection<Long> receiverIds, boolean automatic) {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null) {
            if (messagingEnabled || automatic) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }

        ZonedDateTime occurredAt = escalatedAt.atZone(automatic ? ZoneId.of("Asia/Shanghai") : ZoneId.systemDefault());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("escalationSource", automatic ? "AUTO" : "MANUAL");
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("handlerId", ticket.getHandlerId());
        payload.put("fromEscalationLevel", fromLevel);
        payload.put("escalationLevel", ticket.getEscalatedLevel());
        payload.put("slaStatus", ticket.getSlaStatus());
        payload.put("status", ticket.getStatus());
        payload.put("reason", reason);
        payload.put("escalatedBy", escalationLog.getOperatorId());
        payload.put("escalatedAt", occurredAt.toOffsetDateTime().toString());
        payload.put("escalationLogId", escalationLog.getId());
        ArrayNode receivers = payload.putArray("receiverIds");
        for (Long receiverId : receiverIds) {
            receivers.add(receiverId);
        }
        putDateTime(payload, "responseDeadline", ticket.getResponseDeadline());
        putDateTime(payload, "resolutionDeadline", ticket.getResolutionDeadline());

        WorkOrderEvent event = WorkOrderEvent.of(
                EventType.TICKET_ESCALATED,
                        "TICKET",
                        String.valueOf(ticket.getId()),
                        payload)
                .withActorId(automatic ? "SYSTEM" : String.valueOf(escalationLog.getOperatorId()));
        event.setOccurredAt(occurredAt.toOffsetDateTime());
        TicketEscalatedPayload.from(event);
        publisher.publish(event, topic, TAG);
    }

    /** 将工单的本地截止时间固化为带时区的事件字段。 */
    private void putDateTime(ObjectNode payload, String name, LocalDateTime value) {
        if (value == null) {
            payload.putNull(name);
        } else {
            payload.put(name, value.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        }
    }
}
