package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketRepliedPayload;
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
import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;

/**
 * @author Virgor
 * @date 2026年09月28日 03:14
 * @description 工单回复事件发布器
 */
@Component
public class TicketRepliedEventPublisher {
    /** 默认工单事件 Topic。 */
    static final String DEFAULT_TOPIC = "wo-ticket-event";
    /** 回复事件标签。 */
    static final String TAG = "REPLIED";

    // 事务性 Outbox 发布器
    private final Optional<DomainEventPublisher> domainEventPublisher;
    // JSON 序列化工具
    private final ObjectMapper objectMapper;
    // 当前环境的工单事件 Topic
    private final String topic;
    // 是否启用消息底座
    private final boolean messagingEnabled;

    /**
     * 创建工单回复事件发布器。
     *
     * @param domainEventPublisher 事务性 Outbox 发布器
     * @param topic 工单事件 Topic
     * @param messagingEnabled 是否启用消息底座
     * @param objectMapper JSON 序列化组件
     */
    public TicketRepliedEventPublisher(Optional<DomainEventPublisher> domainEventPublisher,
                                       @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic,
                                       @Value("${work-order.messaging.enabled:false}") boolean messagingEnabled,
                                       ObjectMapper objectMapper) {
        this.domainEventPublisher = domainEventPublisher;
        this.topic = topic;
        this.messagingEnabled = messagingEnabled;
        this.objectMapper = objectMapper;
    }

    /**
     * 将回复事实快照写入当前业务事务的 Outbox。
     * 完整回复正文和附件地址不会进入事件载荷。
     *
     * @param ticket 被回复的工单
     * @param replyLog 已持久化并取得主键的回复操作日志
     * @param receiverId 当前业务规则计算出的通知接收人，可为空
     * @param repliedAt 回复发生时间
     * @param attachmentCount 回复附件数量
     */
    public void publish(Tickets ticket,
                        TicketOperationLog replyLog,
                        Long receiverId,
                        LocalDateTime repliedAt,
                        int attachmentCount) {

        DomainEventPublisher publisher = domainEventPublisher.orElse(null);

        if (publisher == null) {
            if (messagingEnabled) {
                throw new IllegalStateException("消息底座已启用，但 DomainEventPublisher 未创建");
            }
            return;
        }

        ZonedDateTime occurredAt = repliedAt.atZone(ZoneId.systemDefault());

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticket.getId());
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("ticketTitle", ticket.getTitle());
        payload.put("replyLogId", replyLog.getId());
        payload.put("replyType", replyLog.getAction());
        payload.put("replierId", replyLog.getOperatorId());
        payload.put("replierRole", replyLog.getOperatorRole());
        payload.put("repliedAt", occurredAt.toOffsetDateTime().toString());
        payload.put("hasText", hasText(replyLog.getContent()));
        payload.put("attachmentCount", attachmentCount);

        // 添加接收者ID
        ArrayNode receiverIds = payload.putArray("receiverIds");
        if (receiverId != null && !receiverId.equals(replyLog.getOperatorId())) {
            receiverIds.add(receiverId);
        }

        WorkOrderEvent event = WorkOrderEvent.of(
                EventType.TICKET_REPLIED,
                "TICKET",
                String.valueOf(ticket.getId()),
                payload
        ).withActorId(String.valueOf(replyLog.getOperatorId()));

        event.setOccurredAt(occurredAt.toOffsetDateTime());
        TicketRepliedPayload.from(event);
        publisher.publish(event, topic, TAG);
    }


}
