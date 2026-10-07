package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketCancelledPayload;
import com.WorkOrder.ticket.contract.TicketClosedPayload;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 在关闭或撤销工单的事务中写入终态事件 Outbox。
 */
@Component
public class TicketTerminalEventPublisher {
    /**
     * 关闭事件标签。
     */
    static final String CLOSED_TAG = "CLOSED";
    /**
     * 撤销事件标签。
     */
    static final String CANCELLED_TAG = "CANCELLED";

    /**
     * 与工单业务更新共用数据库事务的发布器。
     */
    private final Optional<DomainEventPublisher> domainEventPublisher;
    /**
     * 工单事件 Topic。
     */
    private final String topic;
    /**
     * 消息底座开关。
     */
    private final boolean messagingEnabled;
    /**
     * 载荷 JSON 构建器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建终态事件发布器；关闭消息底座时允许缺少 Outbox 组件。
     *
     * @param domainEventPublisher 与工单业务更新共用数据库事务的发布器
     * @param topic 工单事件 Topic
     * @param messagingEnabled 消息底座开关
     * @param objectMapper 载荷 JSON 构建器
     */
    public TicketTerminalEventPublisher(
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
     * 发布确认关闭事实，关闭时间与事件时间保持一致。
     *
     * @param ticket 工单
     * @param closedBy 已关闭By
     */
    public void publishClosed(Tickets ticket, long closedBy) {
        DomainEventPublisher publisher = publisher();
        if (publisher == null) {
            return;
        }
        OffsetDateTime closedAt = offset(ticket.getClosedAt());
        ObjectNode payload = commonPayload(ticket, "RESOLVED", closedBy);
        payload.put("closedBy", closedBy);
        payload.put("closedAt", closedAt.toString());
        WorkOrderEvent event = event(EventType.TICKET_CLOSED, ticket, closedBy, closedAt, payload);
        TicketClosedPayload.from(event);
        publisher.publish(event, topic, CLOSED_TAG);
    }

    /**
     * 发布撤销事实，撤销时间只保存在历史和事件中，不混用关闭时间。
     *
     * @param ticket 工单
     * @param fromStatus from状态
     * @param cancelledBy 已取消By
     * @param cancelledAt 已取消时间
     */
    public void publishCancelled(Tickets ticket, String fromStatus,
                                 long cancelledBy, LocalDateTime cancelledAt) {
        DomainEventPublisher publisher = publisher();
        if (publisher == null) {
            return;
        }
        OffsetDateTime at = offset(cancelledAt);
        ObjectNode payload = commonPayload(ticket, fromStatus, cancelledBy);
        payload.put("cancelledBy", cancelledBy);
        payload.put("cancelledAt", at.toString());
        WorkOrderEvent event = event(EventType.TICKET_CANCELLED, ticket, cancelledBy, at, payload);
        TicketCancelledPayload.from(event);
        publisher.publish(event, topic, CANCELLED_TAG);
    }

    /**
     * 获取发布器；启用消息却缺少组件时使业务事务失败。
     *
     * @return Domain事件发布器
     */
    private DomainEventPublisher publisher() {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null && messagingEnabled) {
            throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
        }
        return publisher;
    }

    /**
     * 固化终态事件共享的工单字段和接收人顺序。
     *
     * @param ticket 工单
     * @param fromStatus from状态
     * @param actorId 本次操作的用户 ID
     * @return 对象Node
     */
    private ObjectNode commonPayload(Tickets ticket, String fromStatus, long actorId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("creatorId", ticket.getCreatorId());
        if (ticket.getHandlerId() == null) {
            payload.putNull("handlerId");
        } else {
            payload.put("handlerId", ticket.getHandlerId());
        }
        payload.put("fromStatus", fromStatus);
        payload.put("status", ticket.getStatus());
        ArrayNode receivers = payload.putArray("receiverIds");
        if (ticket.getCreatorId() != actorId) {
            receivers.add(ticket.getCreatorId());
        }
        if (ticket.getHandlerId() != null && ticket.getHandlerId() != actorId
                && !ticket.getHandlerId().equals(ticket.getCreatorId())) {
            receivers.add(ticket.getHandlerId());
        }
        putTime(payload, "responseDeadline", ticket.getResponseDeadline());
        putTime(payload, "resolutionDeadline", ticket.getResolutionDeadline());
        return payload;
    }

    /**
     * 构造与业务动作时间一致的统一事件信封。
     *
     * @param type 目标类型
     * @param ticket 工单
     * @param actorId 本次操作的用户 ID
     * @param occurredAt occurred时间
     * @param payload 事件载荷
     * @return 工单领域事件
     */
    private WorkOrderEvent event(EventType type, Tickets ticket, long actorId,
                                 OffsetDateTime occurredAt, ObjectNode payload) {
        WorkOrderEvent event = WorkOrderEvent.of(type, "TICKET", String.valueOf(ticket.getId()), payload)
                .withActorId(String.valueOf(actorId));
        event.setOccurredAt(occurredAt);
        return event;
    }

    /**
     * 将本地时间转换为带偏移量的事件时间。
     *
     * @param value 待处理的值
     * @return 带时区偏移的日期时间
     */
    private OffsetDateTime offset(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * 写入 SLA 截止时间字段。
     *
     * @param payload 事件载荷
     * @param field 待读取或校验的字段名
     * @param value 待处理的值
     */
    private void putTime(ObjectNode payload, String field, LocalDateTime value) {
        if (value == null) {
            payload.putNull(field);
        } else {
            payload.put(field, offset(value).toString());
        }
    }
}
