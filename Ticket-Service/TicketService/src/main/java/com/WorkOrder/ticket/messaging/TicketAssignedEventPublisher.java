package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketAssignedPayload;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 组装并发布管理员或系统派单事件。
 */
@Component
public class TicketAssignedEventPublisher {
    /**
     * 默认工单事件 Topic。
     */
    static final String DEFAULT_TOPIC = "wo-ticket-event";
    /**
     * 派单事件标签。
     */
    static final String TAG = "ASSIGNED";

    // 事务性 Outbox 发布器
    private final Optional<DomainEventPublisher> domainEventPublisher;
    // JSON 序列化工具
    private final ObjectMapper objectMapper;
    // 当前环境的工单事件 Topic
    private final String topic;
    // 是否启用消息底座
    private final boolean messagingEnabled;

    /**
     * 创建派单事件发布器。消息底座显式关闭时 Optional 为空，派单业务保持可用。
     *
     * @param domainEventPublisher 事务性 Outbox 发布器
     * @param objectMapper 统一 JSON 序列化组件
     * @param topic 当前环境的工单事件 Topic
     * @param messagingEnabled 是否启用消息底座
     */
    public TicketAssignedEventPublisher(Optional<DomainEventPublisher> domainEventPublisher,
                                        ObjectMapper objectMapper,
                                        @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic,
                                        @Value("${work-order.messaging.enabled:false}") boolean messagingEnabled) {
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.messagingEnabled = messagingEnabled;
    }

    /**
     * 把派单事实作为稳定快照写入当前业务事务的 Outbox。
     *
     * @param ticket 已更新处理人和状态的工单
     * @param handlerId 新处理人 ID
     * @param operatorId 派单操作人 ID
     * @param assignedAt 派单发生时间
     * @param reason 派单原因
     */
    public void publish(Tickets ticket, Long handlerId, Long operatorId,
                        LocalDateTime assignedAt, String reason) {
        publishEvent(ticket, handlerId, operatorId, "ADMIN", assignedAt, reason);
    }

    /**
     * 将系统自动派单结果写入当前事务的 Outbox。
     *
     * @param ticket 已分配的工单
     * @param handlerId 新处理人 ID
     * @param assignedAt 派单时间
     * @param reason 派单说明
     */
    public void publishSystem(Tickets ticket, Long handlerId, LocalDateTime assignedAt,
                              String reason) {
        publishEvent(ticket, handlerId, null, "SYSTEM", assignedAt, reason);
    }

    /**
     * 组装并校验手动或系统派单事件。
     *
     * @param ticket 已更新的工单
     * @param handlerId 新处理人 ID
     * @param operatorId 手动派单操作人 ID，系统派单时为空
     * @param operatorRole ADMIN 或 SYSTEM
     * @param assignedAt 派单业务时间
     * @param reason 派单说明
     */
    private void publishEvent(Tickets ticket, Long handlerId, Long operatorId,
                              String operatorRole, LocalDateTime assignedAt, String reason) {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null) {
            if (messagingEnabled) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }

        ZonedDateTime occurredAt = assignedAt.atZone(ZoneId.systemDefault());
        // 创建事件载荷
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("handlerId", handlerId);
        if (operatorId == null) {
            payload.putNull("assignedBy");
        } else {
            payload.put("assignedBy", operatorId);
        }
        payload.put("assignedByRole", operatorRole);
        payload.put("assignedAt", occurredAt.toOffsetDateTime().toString());
        payload.put("reason", reason);

        WorkOrderEvent event = WorkOrderEvent.of(
                        EventType.TICKET_ASSIGNED,
                        "TICKET",
                        String.valueOf(ticket.getId()),
                        payload)
                .withActorId(operatorId == null ? "SYSTEM" : String.valueOf(operatorId));
        // 设置事件发生时间
        event.setOccurredAt(occurredAt.toOffsetDateTime());
        // 发布事件
        TicketAssignedPayload.from(event);
        publisher.publish(event, topic, TAG);
    }
}
