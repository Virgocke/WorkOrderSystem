package com.WorkOrder.ticket.messaging;

import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketTransferredPayload;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/** 组装并发布工单转派事件。 */
@Component
public class TicketTransferredEventPublisher {

    /** 默认工单事件 Topic。 */
    static final String DEFAULT_TOPIC = "wo-ticket-event";
    /** 转派事件标签。 */
    static final String TAG = "TRANSFERRED";

    /** 与工单事务共用数据库事务的 Outbox 发布器。 */
    private final Optional<DomainEventPublisher> domainEventPublisher;
    /** 当前工单事件 Topic。 */
    private final String topic;
    /** 消息底座是否启用。 */
    private final boolean messagingEnabled;
    /** 事件载荷构建器。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建工单转派事件发布器。
     *
     * @param domainEventPublisher 事务性 Outbox 发布器
     * @param topic 工单事件 Topic
     * @param messagingEnabled 是否启用消息底座
     * @param objectMapper JSON 序列化组件
     */
    public TicketTransferredEventPublisher(
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
     * 将转派后的责任人、审计记录和当前 SLA 截止时间快照写入业务事务的 Outbox。
     * 本事件只记录责任转移事实，不重置响应或解决截止时间。
     *
     * @param ticket 已更新处理人与分配时间的工单
     * @param fromHandlerId 原处理人 ID
     * @param transferredBy 转派操作人 ID
     * @param transferredByRole 转派操作人角色
     * @param transferredAt 转派发生时间
     * @param reason 转派原因
     * @param transferLog 已持久化的转派操作日志
     * @param assignmentRecord 已持久化的分配记录
     */
    public void publish(Tickets ticket,
                        Long fromHandlerId,
                        Long transferredBy,
                        String transferredByRole,
                        LocalDateTime transferredAt,
                        String reason,
                        TicketOperationLog transferLog,
                        AssignmentRecord assignmentRecord) {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null) {
            if (messagingEnabled) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }

        ZonedDateTime occurredAt = transferredAt.atZone(ZoneId.systemDefault());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("fromHandlerId", fromHandlerId);
        payload.put("toHandlerId", ticket.getHandlerId());
        payload.put("transferredBy", transferredBy);
        payload.put("transferredByRole", transferredByRole);
        payload.put("transferredAt", occurredAt.toOffsetDateTime().toString());
        payload.put("status", ticket.getStatus());
        payload.put("reason", reason);
        payload.put("transferLogId", transferLog.getId());
        payload.put("assignmentRecordId", assignmentRecord.getId());
        putDateTime(payload, "responseDeadline", ticket.getResponseDeadline());
        putDateTime(payload, "resolutionDeadline", ticket.getResolutionDeadline());

        WorkOrderEvent event = WorkOrderEvent.of(
                        EventType.TICKET_TRANSFERRED,
                        "TICKET",
                        String.valueOf(ticket.getId()),
                        payload)
                .withActorId(String.valueOf(transferredBy));
        event.setOccurredAt(occurredAt.toOffsetDateTime());
        TicketTransferredPayload.from(event);
        publisher.publish(event, topic, TAG);
    }

    /** 将可空的本地时间转换为带时区的事件字段。 */
    private void putDateTime(ObjectNode payload, String fieldName, LocalDateTime value) {
        if (value == null) {
            payload.putNull(fieldName);
            return;
        }
        payload.put(fieldName, value.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
    }
}
