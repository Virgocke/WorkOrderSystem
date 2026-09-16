package com.WorkOrder.admin.service.impl;

import com.WorkOrder.admin.service.AdminTicketService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.handler.dto.AdminTicketListDto;
import com.WorkOrder.handler.dto.AssignTicketDto;
import com.WorkOrder.handler.mapper.AssignmentRecordMapper;
import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.enums.TicketStatusEnum;
import com.WorkOrder.ticket.feignclient.UserFeignClient;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月17日 01:55
 * @description 管理员工单服务实现类
 */
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {
    
    private final UserFeignClient userFeignClient;
    private final TicketMapper ticketMapper;
    private final AssignmentRecordMapper assignmentRecordMapper;
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketOperationLogMapper ticketOperationLogMapper;
    
    @Override
    public List<TicketResponse> getTicketListForAdmin(Long adminId, AdminTicketListDto adminTicketListDto) {
        Result<UserProfile> result = userFeignClient.getById(adminId);
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }

        // 验证用户角色，确保是管理员
        UserProfile userProfile = result.getData();
        if (userProfile.getRole() != 2) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        if (userProfile.getStatus() == 0) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        LambdaQueryWrapper<Tickets> queryWrapper = new LambdaQueryWrapper<Tickets>();
        // 按状态查询
        if (adminTicketListDto.getStatus() != null){
            queryWrapper.eq(Tickets::getStatus, adminTicketListDto.getStatus());
        }
        // 按优先级查询
        if (adminTicketListDto.getPriority() != 0){
            queryWrapper.eq(Tickets::getPriority, adminTicketListDto.getPriority());
        }
        // 按类别查询
        if (adminTicketListDto.getCategoryId() != null){
            queryWrapper.eq(Tickets::getCategoryId, adminTicketListDto.getCategoryId());
        }
        // 按处理人查询
        if (adminTicketListDto.getHandlerId() != null){
            queryWrapper.eq(Tickets::getHandlerId, adminTicketListDto.getHandlerId());
        }
        // 按创建人查询
        if (adminTicketListDto.getCreatorId() != null){
            queryWrapper.eq(Tickets::getCreatorId, adminTicketListDto.getCreatorId());
        }
        // 按SLA状态查询
        if (adminTicketListDto.getSlaStatus() != null){
            queryWrapper.eq(Tickets::getSlaStatus, adminTicketListDto.getSlaStatus());
        }
        // 按时间范围查询
        if (adminTicketListDto.getStart() != null && adminTicketListDto.getEnd() != null){
            queryWrapper.between(Tickets::getCreatedAt, adminTicketListDto.getStart(), adminTicketListDto.getEnd());
        }
        // todo按关键字查询，等搜索模块完善之后用搜索模块代替
        if (adminTicketListDto.getKeyword() != null){
            queryWrapper.like(Tickets::getTitle, adminTicketListDto.getKeyword())
                    .or()
                    .like(Tickets::getDescription, adminTicketListDto.getKeyword());
        }
        queryWrapper.orderByDesc(Tickets::getCreatedAt);
        queryWrapper.orderByDesc(Tickets::getId);

        Page<Tickets> page = new Page<>(adminTicketListDto.getPage(), adminTicketListDto.getPageSize());

        Page<Tickets> tickets = ticketMapper.selectPage(page, queryWrapper);

        if (tickets.getRecords() == null || tickets.getRecords().isEmpty()) {
            return Collections.emptyList();
        }
        // 获取工单列表
        List<Tickets> records = tickets.getRecords();
        // 转换为响应对象
        List<TicketResponse> ticketResponses = records.stream().map(TicketConverter::toResponse).collect(Collectors.toList());

        return ticketResponses;
    }

    @Override
    @Transactional
    public TicketResponse assignTicket(
            Long ticketId,
            AssignTicketDto assignTicketDto,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        // 操作人角色取自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        String oldStatus = ticket.getStatus();
        Long oldHandlerId = ticket.getHandlerId();
        boolean isPendingAssign = TicketStatusEnum.PENDING_ASSIGN.name().equals(oldStatus);
        // 已有处理人且非待分配的工单必须使用转派接口，包括重复分配给同一处理人。
        if (oldHandlerId != null && !isPendingAssign) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }

        // 校验目标处理人存在，且为启用的处理人账号。
        Long handlerId = assignTicketDto.getHandlerId();
        Result<UserProfile> result = userFeignClient.getById(handlerId);
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        UserProfile handler = result.getData();
        if (handler.getRole() != 1 || handler.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        String reason = assignTicketDto.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "管理员手动分配";
        }

        // 获取当前时间
        LocalDateTime assignedAt = LocalDateTime.now();
        // 设置处理人ID与分配时间
        ticket.setHandlerId(handlerId);
        ticket.setAssignedAt(assignedAt);
        if (isPendingAssign) {
            ticket.setStatus(TicketStatusEnum.PENDING_RESPONSE.name());
        }

        // 同时核对原状态与原处理人，避免并发分配、转派或状态流转被覆盖。
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, oldStatus)
                .isNull(oldHandlerId == null, Tickets::getHandlerId)
                .eq(oldHandlerId != null, Tickets::getHandlerId, oldHandlerId)
                .set(Tickets::getHandlerId, handlerId)
                .set(Tickets::getAssignedAt, assignedAt)
                .set(Tickets::getStatus, ticket.getStatus()));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 分配记录与工单、状态历史、操作日志在同一事务中保存。
        AssignmentRecord assignmentRecord = new AssignmentRecord();
        assignmentRecord.setTicketId(ticketId);
        assignmentRecord.setHandlerId(handlerId);
        assignmentRecord.setAssignedBy("MANUAL");
        // TODO 接入 Assign-Engine，按工单和目标处理人重新计算综合分及各项得分，替换占位值。
        assignmentRecord.setScore(BigDecimal.ZERO);
        assignmentRecord.setSkillMatchScore(BigDecimal.ZERO);
        assignmentRecord.setLoadScore(BigDecimal.ZERO);
        assignmentRecord.setSlaScore(BigDecimal.ZERO);
        assignmentRecord.setRatingScore(BigDecimal.ZERO);
        assignmentRecord.setCreatedAt(assignedAt);
        if (assignmentRecordMapper.insert(assignmentRecord) != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 状态历史
        if (!saveTicketStatusHistory(ticket, oldStatus, operatorId, reason, "ASSIGN", assignedAt)) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 操作日志
        if (!saveTicketOperationLog(
                ticket,
                "ASSIGN",
                operatorId,
                operatorRole,
                clientIp,
                "手动分配工单至处理人ID：" + handlerId + "，原因：" + reason,
                assignedAt)) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // TODO 接入用户服务的处理人负载维护，按新旧处理人及工单状态更新在办工单数。
        // TODO 接入 Notification-Service，在事务提交后通知新处理人，避免回滚后发送通知。

        // updated_at 由数据库自动维护，重新查询以返回数据库中的最新字段值。
        Tickets assignedTicket = ticketMapper.selectById(ticketId);
        if (assignedTicket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        TicketResponse response = TicketConverter.toResponse(assignedTicket);
        response.setHandlerName(handler.getRealName());
        return response;
    }

    /**
     * 保存工单状态历史
     * @param ticket 变更后的工单
     * @param oldStatus 旧状态
     * @param operatorId 操作人ID
     * @param remark 备注
     * @param event 触发事件
     * @param createdAt 事件发生时间
     * @return 是否保存成功
     */
    private boolean saveTicketStatusHistory(
            Tickets ticket,
            String oldStatus,
            Long operatorId,
            String remark,
            String event,
            LocalDateTime createdAt) {

        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticket.getId());
        history.setFromStatus(oldStatus);
        history.setToStatus(ticket.getStatus());
        history.setEvent(event);
        history.setOperatorId(operatorId);
        history.setRemark(remark);
        history.setCreatedAt(createdAt);
        return ticketStatusHistoryMapper.insert(history) == 1;
    }

    /**
     * 保存工单操作日志
     * @param ticket 工单
     * @param action 操作类型
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @param content 操作内容
     * @param createdAt 事件发生时间
     * @return 是否保存成功
     */
    private boolean saveTicketOperationLog(
            Tickets ticket,
            String action,
            Long operatorId,
            String operatorRole,
            String clientIp,
            String content,
            LocalDateTime createdAt) {

        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticket.getId());
        log.setAction(action);
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorRole);
        log.setIpAddress(clientIp);
        log.setContent(content);
        log.setCreatedAt(createdAt);
        return ticketOperationLogMapper.insert(log) == 1;
    }
}
