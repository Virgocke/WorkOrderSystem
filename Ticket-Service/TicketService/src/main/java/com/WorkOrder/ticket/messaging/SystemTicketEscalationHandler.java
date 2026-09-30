package com.WorkOrder.ticket.messaging;

import com.WorkOrder.model.ticket.EscalationRule;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketSlaConfigurationMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 在消费事务内重新判定升级，固定接收人并保存升级事实，禁止使用扫描时的陈旧状态。 */
@Component
@RequiredArgsConstructor
public class SystemTicketEscalationHandler {
    /** 源工单锁和更新入口。 */
    private final TicketMapper ticketMapper;
    /** 执行时最新规则读取入口。 */
    private final TicketSlaConfigurationMapper configurationMapper;
    /** 配置 JSON 解析器。 */
    private final ObjectMapper objectMapper;
    /** 状态历史入口。 */
    private final TicketStatusHistoryMapper historyMapper;
    /** 操作日志入口。 */
    private final TicketOperationLogMapper operationLogMapper;
    /** 同事务升级事件发布器。 */
    private final TicketEscalatedEventPublisher publisher;

    /**
     * 自动升至当前满足的最高级别；同级重复请求、已解决工单和已停用规则均无副作用。
     * 无可通知的启用用户时抛错回滚，由消息重试，避免形成无人接收的升级。
     * @param ticketId 需要重新检查的工单 ID
     * @return 是否发生升级
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean confirm(long ticketId) {
        Tickets ticket = ticketMapper.selectForEscalation(ticketId);
        if (ticket == null || ticket.getEscalatedLevel() >= 3) {
            return false;
        }
        String raw = configurationMapper.selectEscalationRules();
        if (raw == null) {
            return false;
        }
        List<EscalationRule> rules;
        try {
            rules = EscalationRule.fromJson(objectMapper.readTree(raw));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的升级规则不合法", exception);
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        EscalationRule matched = null;
        for (EscalationRule rule : rules) {
            if (rule.getLevel() > ticket.getEscalatedLevel()
                    && rule.matches(ticket.getStatus(), ticket.getFirstResponseAt(),
                    ticket.getResponseDeadline(), ticket.getResolutionDeadline(), now)) {
                matched = rule;
            }
        }
        if (matched == null) {
            return false;
        }
        List<Long> receivers = Collections.emptyList();
        if ("MANAGER".equals(matched.getTargetRole())) {
            Long manager = ticketMapper.selectActiveDepartmentManager(
                    ticket.getHandlerId() == null ? ticket.getCreatorId() : ticket.getHandlerId());
            if (manager != null && manager > 0) {
                receivers = Collections.singletonList(manager);
            }
        }
        if (receivers.isEmpty()) {
            receivers = ticketMapper.selectActiveAdminIds().stream()
                    .filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        }
        if (receivers.isEmpty()) {
            throw new IllegalStateException("自动升级没有可用的通知接收人");
        }
        int fromLevel = ticket.getEscalatedLevel();
        String reason = "SLA自动升级：响应截止后" + matched.getResponseTimeoutMin()
                + "分钟或解决截止后" + matched.getResolutionTimeoutMin() + "分钟，目标"
                + matched.getTargetRole() + "，级别" + fromLevel + "→" + matched.getLevel();
        if (ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId).eq(Tickets::getStatus, ticket.getStatus())
                .eq(Tickets::getEscalatedLevel, fromLevel)
                .set(Tickets::getEscalatedLevel, matched.getLevel())
                .set(Tickets::getSlaStatus, "ESCALATED")) != 1) {
            throw new IllegalStateException("自动升级工单状态更新失败");
        }
        ticket.setEscalatedLevel(matched.getLevel());
        ticket.setSlaStatus("ESCALATED");
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticketId);
        history.setFromStatus(ticket.getStatus());
        history.setToStatus(ticket.getStatus());
        history.setEvent("ESCALATE");
        history.setRemark(reason);
        history.setCreatedAt(now);
        if (historyMapper.insert(history) != 1) {
            throw new IllegalStateException("自动升级状态历史写入失败");
        }
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticketId);
        log.setAction("ESCALATE");
        log.setOperatorRole("SYSTEM");
        log.setContent(reason);
        log.setCreatedAt(now);
        if (operationLogMapper.insert(log) != 1 || log.getId() == null) {
            throw new IllegalStateException("自动升级操作日志写入失败");
        }
        publisher.publishSystem(ticket, fromLevel, reason, now, log, receivers);
        return true;
    }
}
