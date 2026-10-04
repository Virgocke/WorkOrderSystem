package com.WorkOrder.ticket.messaging;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.ticket.contract.TicketSearchChangedPayload;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Optional;

/** 在工单业务事务中登记搜索变更，版本始终取自本次更新后的数据库行。 */
@Component
public class TicketSearchChangePublisher {
    public static final String TAG = "CHANGED";
    private static final String PRODUCER = "ticket-service";

    private final Optional<DomainEventPublisher> domainEventPublisher;
    private final TicketMapper ticketMapper;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final boolean publishEnabled;
    private final boolean messagingEnabled;
    private final String sourceService;

    public TicketSearchChangePublisher(
            Optional<DomainEventPublisher> domainEventPublisher,
            TicketMapper ticketMapper,
            ObjectMapper objectMapper,
            @Value("${work-order.messaging.ticket-search-topic:wo-ticket-search-event}") String topic,
            @Value("${work-order.search-projection.publish-enabled:false}") boolean publishEnabled,
            @Value("${work-order.messaging.enabled:false}") boolean messagingEnabled,
            @Value("${work-order.messaging.outbox.source-service:}") String sourceService) {
        this.domainEventPublisher = domainEventPublisher;
        this.ticketMapper = ticketMapper;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.publishEnabled = publishEnabled;
        this.messagingEnabled = messagingEnabled;
        this.sourceService = sourceService;
    }

    /** 依赖注入完成后检查配置，避免启用发布却静默丢弃搜索事件。 */
    @PostConstruct
    public void validateConfiguration() {
        if (!publishEnabled) {
            return;
        }
        if (!messagingEnabled || !domainEventPublisher.isPresent()) {
            throw new IllegalStateException("搜索事件发布已启用，要求 work-order.messaging.enabled=true"
                    + " 且 DomainEventPublisher 可用");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalStateException("work-order.messaging.ticket-search-topic 不能为空");
        }
        if (!PRODUCER.equals(sourceService)) {
            throw new IllegalStateException("搜索事件发布要求 work-order.messaging.outbox.source-service=ticket-service");
        }
    }

    /**
     * 成功修改工单后调用；业务数据、源版本、历史记录和 Outbox 由同一事务提交。
     * 关闭发布时不查询版本，但业务 SQL 仍然递增版本。
     *
     * @param ticketId 当前事务已经成功插入或更新的工单 ID
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void publish(Long ticketId) {
        if (!publishEnabled) {
            return;
        }
        if (ticketId == null || ticketId <= 0) {
            throw new IllegalArgumentException("ticketId 必须为正数");
        }
        Long version = ticketMapper.selectSourceVersion(ticketId);
        if (version == null || version <= 0) {
            throw new IllegalStateException("工单不存在或 source_version 非法，ticketId=" + ticketId);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketId", ticketId);
        // 构建事件
        WorkOrderEvent event = WorkOrderEvent.of(EventType.TICKET_SEARCH_CHANGED,
                "TICKET", String.valueOf(ticketId), payload)
                .withAggregateVersion(version);
        // 设置生产者
        event.setProducer(PRODUCER);
        // 检验事件
        TicketSearchChangedPayload.from(event);
        // 发布事件
        domainEventPublisher.orElseThrow(() -> new IllegalStateException(
                "搜索事件发布已启用，但 DomainEventPublisher 不可用"))
                .publish(event, topic, TAG);
    }
}
