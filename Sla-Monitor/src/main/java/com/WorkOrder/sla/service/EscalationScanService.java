package com.WorkOrder.sla.service;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.model.ticket.EscalationRule;
import com.WorkOrder.sla.mapper.EscalationScanMapper;
import com.WorkOrder.ticket.contract.EscalationRequestedPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** 将一页超时工单的升级检查请求原子写入 SLA 服务 Outbox。 */
@Service
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
public class EscalationScanService {
    /** 规则与候选工单查询。 */
    private final EscalationScanMapper mapper;
    /** JSON 编解码器。 */
    private final ObjectMapper objectMapper;
    /** 事务性请求发布器。 */
    private final DomainEventPublisher publisher;
    /** 工单事件主题。 */
    private final String topic;

    /** 注入扫描与消息组件。 */
    public EscalationScanService(EscalationScanMapper mapper, ObjectMapper objectMapper,
                                DomainEventPublisher publisher,
                                @Value("${work-order.messaging.ticket-topic:wo-ticket-event}") String topic) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.topic = topic;
    }

    /** 每页读取当前规则，最多发布 200 个请求；返回下一页游标，0 表示本轮结束。 */
    @Transactional(rollbackFor = Exception.class)
    public long scanPage(long afterId) {
        String raw = mapper.selectRules();
        if (raw == null) {
            return 0;
        }
        List<EscalationRule> rules;
        try {
            rules = EscalationRule.fromJson(objectMapper.readTree(raw));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的升级规则不合法", exception);
        }
        if (rules.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
        // 查询候选工单
        List<Long> ids = mapper.selectCandidates(afterId, now.toLocalDateTime(), rules);
        for (Long id : ids) {
            WorkOrderEvent event = WorkOrderEvent.of(
                    EventType.ESCALATION_REQUESTED,
                            "TICKET",
                    String.valueOf(id),
                            objectMapper.createObjectNode().put("ticketId", id))
                    .withActorId("SYSTEM");
            event.setOccurredAt(now);
            EscalationRequestedPayload.ticketId(event);
            publisher.publish(event, topic, "ESCALATION_REQUESTED");
        }
        return ids.size() < 200 ? 0 : ids.get(ids.size() - 1);
    }
}
