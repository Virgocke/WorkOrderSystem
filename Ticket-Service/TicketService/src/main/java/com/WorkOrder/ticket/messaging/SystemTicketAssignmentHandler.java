package com.WorkOrder.ticket.messaging;

import com.WorkOrder.handler.mapper.AssignmentRecordMapper;
import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.ticket.contract.AssignmentProposedPayload;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.model.Tickets;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 在 Ticket-Service 的消费事务内确认自动派单及全部审计记录。
 */
@Component
public class SystemTicketAssignmentHandler {
    /**
     * 工单条件更新和查询入口。
     */
    private final TicketMapper ticketMapper;
    /**
     * 派单评分记录入口。
     */
    private final AssignmentRecordMapper assignmentRecordMapper;
    /**
     * 工单状态历史入口。
     */
    private final TicketStatusHistoryMapper historyMapper;
    /**
     * 工单操作日志入口。
     */
    private final TicketOperationLogMapper operationLogMapper;
    /**
     * 最终派单事件发布器。
     */
    private final TicketAssignedEventPublisher assignedPublisher;
    /**
     * 同事务登记最终源版本的搜索变更事件。
     */
    private final TicketSearchChangePublisher searchChangePublisher;

    /**
     * 注入同库事务内使用的持久化组件。
     *
     * @param ticketMapper 工单条件更新和查询入口
     * @param assignmentRecordMapper 派单评分记录入口
     * @param historyMapper 工单状态历史入口
     * @param operationLogMapper 工单操作日志入口
     * @param assignedPublisher 最终派单事件发布器
     * @param searchChangePublisher 同事务登记最终源版本的搜索变更事件
     */
    public SystemTicketAssignmentHandler(TicketMapper ticketMapper,
                                         AssignmentRecordMapper assignmentRecordMapper,
                                         TicketStatusHistoryMapper historyMapper,
                                         TicketOperationLogMapper operationLogMapper,
                                         TicketAssignedEventPublisher assignedPublisher,
                                         TicketSearchChangePublisher searchChangePublisher) {
        this.ticketMapper = ticketMapper;
        this.assignmentRecordMapper = assignmentRecordMapper;
        this.historyMapper = historyMapper;
        this.operationLogMapper = operationLogMapper;
        this.assignedPublisher = assignedPublisher;
        this.searchChangePublisher = searchChangePublisher;
    }

    /**
     * 确认仍有效的提议；人工派单或工单状态变化后返回 false。
     * 消费日志、工单更新、审计及最终事件由调用方的同一事务提交。
     *
     * @param proposal 已校验的自动派单提议
     * @return 成功派单时返回 true，陈旧提议返回 false
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean confirm(AssignmentProposedPayload proposal) {
        LocalDateTime assignedAt = LocalDateTime.now();
        ticketMapper.lockHandlerProfile(proposal.getHandlerId());
        // 更新工单状态为 PENDING_RESPONSE
        if (ticketMapper.assignIfPending(proposal.getTicketId(), proposal.getHandlerId(),
                assignedAt) != 1) {
            return false;
        }
        // 读取工单信息
        Tickets ticket = ticketMapper.selectById(proposal.getTicketId());
        if (ticket == null) {
            throw new IllegalStateException("自动派单后无法读取工单");
        }

        AssignmentRecord record = new AssignmentRecord();
        record.setTicketId(proposal.getTicketId());
        record.setHandlerId(proposal.getHandlerId());
        record.setAssignedBy("SYSTEM");
        record.setScore(proposal.getScore());
        record.setSkillMatchScore(proposal.getSkillMatchScore());
        record.setLoadScore(proposal.getLoadScore());
        record.setSlaScore(proposal.getSlaScore());
        record.setRatingScore(proposal.getRatingScore());
        record.setCreatedAt(assignedAt);
        if (assignmentRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("系统派单记录写入失败");
        }

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(proposal.getTicketId());
        history.setFromStatus("PENDING_ASSIGN");
        history.setToStatus("PENDING_RESPONSE");
        history.setEvent("ASSIGN");
        history.setRemark("系统自动派单");
        history.setCreatedAt(assignedAt);
        if (historyMapper.insert(history) != 1) {
            throw new IllegalStateException("系统派单状态历史写入失败");
        }

        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(proposal.getTicketId());
        log.setAction("ASSIGN");
        log.setOperatorRole("SYSTEM");
        log.setContent("系统自动分配工单至处理人 ID：" + proposal.getHandlerId());
        log.setCreatedAt(assignedAt);
        if (operationLogMapper.insert(log) != 1) {
            throw new IllegalStateException("系统派单操作日志写入失败");
        }

        assignedPublisher.publishSystem(ticket, proposal.getHandlerId(), assignedAt,
                "系统自动分配");
        searchChangePublisher.publish(proposal.getTicketId());
        return true;
    }
}
