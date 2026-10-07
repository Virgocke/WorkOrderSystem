package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketResolvedPayload;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 在解决工单事务内发布解决事实。
 */
@Component
public class TicketResolvedEventPublisher {
    /**
     * 解决事件标签。
     */
    static final String TAG = "RESOLVED";

    /**
     * 与工单事务共用数据库事务的 Outbox 发布器。
     */
    private final Optional<DomainEventPublisher> domainEventPublisher;
    /**
     * 当前工单事件 Topic。
     */
    private final String topic;
    /**
     * 消息底座是否启用。
     */
    private final boolean messagingEnabled;
    /**
     * 事件载荷构建器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建解决事件发布器；关闭消息底座时允许缺少 Outbox 发布器。
     *
     * @param domainEventPublisher 与工单事务共用数据库事务的 Outbox 发布器
     * @param topic 当前工单事件 Topic
     * @param messagingEnabled 消息底座是否启用
     * @param objectMapper 事件载荷构建器
     */
    public TicketResolvedEventPublisher(
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
     * 校验解决快照并在当前业务事务中写入 Outbox，方案正文不进入事件。
     *
     * @param ticket 工单
     * @param fromStatus from状态
     * @param resolutionLog 解决日志
     */
    public void publish(Tickets ticket, String fromStatus, TicketOperationLog resolutionLog) {
        DomainEventPublisher publisher = domainEventPublisher.orElse(null);
        if (publisher == null) {
            if (messagingEnabled) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }

        OffsetDateTime resolvedAt = ticket.getResolvedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("creatorId", ticket.getCreatorId());
        payload.put("handlerId", ticket.getHandlerId());
        payload.put("resolvedBy", resolutionLog.getOperatorId());
        payload.put("resolvedByRole", resolutionLog.getOperatorRole());
        payload.put("fromStatus", fromStatus);
        payload.put("status", ticket.getStatus());
        payload.put("resolvedAt", resolvedAt.toString());
        payload.put("resolutionLogId", resolutionLog.getId());
        if (ticket.getCreatorId() != null
                && !ticket.getCreatorId().equals(resolutionLog.getOperatorId())) {
            payload.putArray("receiverIds").add(ticket.getCreatorId());
        } else {
            payload.putArray("receiverIds");
        }
        putTime(payload, "responseDeadline", ticket.getResponseDeadline());
        putTime(payload, "resolutionDeadline", ticket.getResolutionDeadline());

        WorkOrderEvent event = WorkOrderEvent.of(EventType.TICKET_RESOLVED,
                "TICKET", String.valueOf(ticket.getId()), payload)
                .withActorId(String.valueOf(resolutionLog.getOperatorId()));
        event.setOccurredAt(resolvedAt);
        TicketResolvedPayload.from(event);
        publisher.publish(event, topic, TAG);
    }

    /**
     * 将本地截止时间固化为带时区偏移量的事件字段。
     *
     * @param payload 事件载荷
     * @param field 待读取或校验的字段名
     * @param value 待处理的值
     */
    private void putTime(ObjectNode payload, String field, LocalDateTime value) {
        if (value == null) {
            payload.putNull(field);
        } else {
            payload.put(field, value.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        }
    }
}
