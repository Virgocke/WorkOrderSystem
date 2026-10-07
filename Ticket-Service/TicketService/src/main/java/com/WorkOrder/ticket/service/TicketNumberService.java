package com.WorkOrder.ticket.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.ticket.TicketNumberRule;
import com.WorkOrder.ticket.mapper.TicketNumberMapper;
import com.WorkOrder.ticket.model.TicketNumberCounter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 在工单创建事务中按规则分配连续的编号序号。
 */
@Service
@RequiredArgsConstructor
public class TicketNumberService {
    private final TicketNumberMapper ticketNumberMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分配新工单编号。计数器更新与工单写入同事务提交或回滚。
     *
     * @param createdAt 亚洲上海时区的本次工单创建时间
     * @return 包含前缀、日期和补零序号的工单编号
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(LocalDateTime createdAt) {
        // 获取当前规则
        TicketNumberRule rule = currentRule();
        // 获取或初始化计数器
        String scopeKey = rule.scopeKey(createdAt.toLocalDate());
        // 如果计数器不存在则初始化
        if (ticketNumberMapper.insertCounterIfAbsent(scopeKey) == 1) {
            // 获取现有最大序号
            Long existing = ticketNumberMapper.selectMaxExistingSequence(scopeKey);
            // 如果现有最大序号不存在则初始化为0
            if (ticketNumberMapper.seedCounter(scopeKey, existing == null ? 0L : existing) != 1) {
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            }
        }
        // 锁定计数器
        TicketNumberCounter counter = ticketNumberMapper.lockCounter(scopeKey);
        if (counter == null || counter.getLastValue() == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        long lastValue = counter.getLastValue();
        if (lastValue >= rule.maxSequence()) {
            throw new SystemException(SystemExceptionEnum.TICKET_NO_EXHAUSTED);
        }
        if (ticketNumberMapper.incrementCounter(scopeKey, lastValue) != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        return rule.format(scopeKey, lastValue + 1);
    }

    /**
     * 处理 currentRule 对应的工单编号服务操作。
     *
     * @return 已校验的当前规则；数据库缺失时返回系统默认规则
     */
    private TicketNumberRule currentRule() {
        String raw = ticketNumberMapper.selectRuleJson();
        if (raw == null) {
            return TicketNumberRule.defaults();
        }
        try {
            return TicketNumberRule.fromJson(objectMapper.readTree(raw));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的工单编号规则不合法", exception);
        }
    }
}
