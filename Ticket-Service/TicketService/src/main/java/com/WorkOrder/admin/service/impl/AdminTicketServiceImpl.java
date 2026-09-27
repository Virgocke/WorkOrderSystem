package com.WorkOrder.admin.service.impl;

import com.WorkOrder.admin.dto.CloseTicketDto;
import com.WorkOrder.admin.service.AdminTicketService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.admin.dto.AdminTicketListDto;
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
import com.WorkOrder.ticket.messaging.TicketAssignedEventPublisher;
import com.WorkOrder.ticket.service.TicketResponseAttachmentEnricher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final TicketResponseAttachmentEnricher ticketResponseAttachmentEnricher;
    private final TicketAssignedEventPublisher ticketAssignedEventPublisher;
    
    @Override
    @Transactional
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
        String status = adminTicketListDto.getStatus();
        if (status != null) {
            status = status.trim();
        }
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            queryWrapper.eq(Tickets::getStatus, status);
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

        return ticketResponseAttachmentEnricher.enrichAll(ticketResponses);
    }

    /**
     * 分配工单
     * @param ticketId 工单ID
     * @param assignTicketDto 分配参数
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 分配后的工单信息
     */
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

        validateAssignmentStatus(ticket);

        // 校验目标处理人存在，且为启用的处理人账号。
        Long handlerId = assignTicketDto.getHandlerId();
        UserProfile handler = getEnabledHandler(handlerId);

        String reason = assignTicketDto.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "管理员手动分配";
        }

        saveTicketAssignment(ticket, handlerId, reason, operatorId, operatorRole, clientIp, LocalDateTime.now());

        // updated_at 由数据库自动维护，重新查询以返回数据库中的最新字段值。
        Tickets assignedTicket = ticketMapper.selectById(ticketId);
        if (assignedTicket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        TicketResponse response = TicketConverter.toResponse(assignedTicket);
        response.setHandlerName(handler.getRealName());
        return ticketResponseAttachmentEnricher.enrich(response);
    }

    /**
     * 批量分配工单
     * @param ticketIds 工单ID列表
     * @param handlerId 目标处理人ID
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 分配后的工单ID列表
     */
    @Override
    @Transactional
    public List<Long> assignTicketList(
            List<Long> ticketIds,
            Long handlerId,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (ticketIds == null || ticketIds.isEmpty()
                || ticketIds.stream().anyMatch(id -> id == null || id <= 0)
                || handlerId == null || handlerId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 与批量关闭保持一致，校验操作人存在且为启用状态。
        Result<UserProfile> userResult = userFeignClient.getById(operatorId);
        if (userResult == null || userResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (userResult.getData().getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
        // 同一批次只校验一次目标处理人。
        getEnabledHandler(handlerId);

        // 去重并保持请求顺序，一次查询后确认整批工单均存在且允许分配。
        List<Long> assignedTicketIds = ticketIds.stream().distinct().collect(Collectors.toList());
        List<Tickets> tickets = ticketMapper.selectBatchIds(assignedTicketIds);
        if (tickets == null || tickets.size() != assignedTicketIds.size()) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        // 构建工单ID与工单信息的映射，后续根据ID快速获取工单信息
        Map<Long, Tickets> ticketsById = tickets.stream()
                .collect(Collectors.toMap(Tickets::getId, ticket -> ticket));
        for (Long ticketId : assignedTicketIds) {
            validateAssignmentStatus(ticketsById.get(ticketId));
        }

        LocalDateTime assignedAt = LocalDateTime.now();
        for (Long ticketId : assignedTicketIds) {
            saveTicketAssignment(ticketsById.get(ticketId), handlerId, "管理员批量分配",
                    operatorId, operatorRole, clientIp, assignedAt);
        }
        return assignedTicketIds;
    }

    /**
     * 校验工单是否允许分配，已有处理人且非待分配时必须使用转派接口。
     */
    private void validateAssignmentStatus(Tickets ticket) {
        if (ticket.getHandlerId() != null
                && !TicketStatusEnum.PENDING_ASSIGN.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }
    }

    /**
     * 获取启用的处理人账号。
     */
    private UserProfile getEnabledHandler(Long handlerId) {
        Result<UserProfile> result = userFeignClient.getById(handlerId);
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        UserProfile handler = result.getData();
        if (handler.getRole() != 1 || handler.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
        return handler;
    }

    /**
     * 保存分配结果及审计记录，由单个分配或批量分配的外层事务统一提交。
     */
    private void saveTicketAssignment(
            Tickets ticket,
            Long handlerId,
            String reason,
            Long operatorId,
            String operatorRole,
            String clientIp,
            LocalDateTime assignedAt) {

        Long ticketId = ticket.getId();
        String oldStatus = ticket.getStatus();
        Long oldHandlerId = ticket.getHandlerId();
        boolean isPendingAssign = TicketStatusEnum.PENDING_ASSIGN.name().equals(oldStatus);
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
        //todo 接入 Assign-Engine，按工单和目标处理人重新计算综合分及各项得分，替换占位值。
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

        // 与工单、分配记录和审计记录处于同一事务；Outbox 写入失败时整笔派单回滚。
        ticketAssignedEventPublisher.publish(ticket, handlerId, operatorId, assignedAt, reason);

        //todo 接入用户服务的处理人负载维护，按新旧处理人及工单状态更新在办工单数。
    }

    @Override
    @Transactional
    public TicketResponse closeTicket(Long ticketId, CloseTicketDto closeTicketDto, Long operatorId, String operatorRole, String clientIp) {
        // 操作人角色取自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 校验操作人存在且为启用状态
        Result<UserProfile> userResult = userFeignClient.getById(operatorId);
        if (userResult == null || userResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        UserProfile operator = userResult.getData();
        if (operator.getStatus() != 1){
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        // 校验工单存在
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 检查工单状态是否允许关闭
        if (
                TicketStatusEnum.CLOSED.name().equals(ticket.getStatus()) ||
                TicketStatusEnum.CANCELLED.name().equals(ticket.getStatus())
        ){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }

        String oldStatus = ticket.getStatus();

        ticket.setStatus(TicketStatusEnum.CLOSED.name());
        ticket.setClosedAt(LocalDateTime.now());

        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                        .eq(Tickets::getId, ticketId)
                        .eq(Tickets::getStatus, oldStatus)
                        .set(Tickets::getStatus, ticket.getStatus())
                        .set(Tickets::getClosedAt, ticket.getClosedAt()));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        if (!saveTicketStatusHistory(
                ticket,
                oldStatus,
                operatorId,
                closeTicketDto.getReason(),
                "CLOSE",
                ticket.getClosedAt())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        if (!saveTicketOperationLog(
                ticket,
                "CLOSE",
                operatorId,
                operatorRole,
                clientIp,
                "关闭工单，原因：" + closeTicketDto.getReason(),
                ticket.getClosedAt())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        return ticketResponseAttachmentEnricher.enrich(TicketConverter.toResponse(ticket));
    }

    /**
     * 批量关闭工单
     * @param closedTickets 工单ID列表
     * @param reason 关闭原因
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @return 关闭的工单ID列表
     */
    @Override
    @Transactional
    public List<Long> closeTicketList(List<Long> closedTickets, String reason, Long operatorId, String operatorRole, String clientIp) {
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 校验操作人存在且为启用状态
        Result<UserProfile> userResult = userFeignClient.getById(operatorId);
        if (userResult == null || userResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (userResult.getData().getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        if (closedTickets == null || closedTickets.isEmpty()) {
            return Collections.emptyList();
        }
        if (closedTickets.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 去重后一次查询，避免重复关闭，并在写入前确认所有工单存在。
        List<Long> ticketIds = closedTickets.stream().distinct().collect(Collectors.toList());
        // 一次查询所有工单，避免多次查询数据库
        List<Tickets> tickets = ticketMapper.selectBatchIds(ticketIds);
        if (tickets == null || tickets.size() != ticketIds.size()) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        Map<Long, Tickets> ticketsById = tickets.stream()
                .collect(Collectors.toMap(Tickets::getId, ticket -> ticket));

        // 构建关闭原因，如果未提供原因则使用默认值
        String closeReason = reason == null || reason.trim().isEmpty() ? "管理员批量关闭" : reason;
        LocalDateTime closedAt = LocalDateTime.now();
        List<Long> closedTicketIds = new ArrayList<>();
        for (Long ticketId : ticketIds) {
            Tickets ticket = ticketsById.get(ticketId);
            String oldStatus = ticket.getStatus();
            // 终态工单无需再次关闭，也不重复生成审计记录。
            if (TicketStatusEnum.CLOSED.name().equals(oldStatus)
                    || TicketStatusEnum.CANCELLED.name().equals(oldStatus)) {
                continue;
            }

            // 核对查询时的状态，避免覆盖并发发生的状态变更。
            int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                    .eq(Tickets::getId, ticketId)
                    .eq(Tickets::getStatus, oldStatus)
                    .set(Tickets::getStatus, TicketStatusEnum.CLOSED.name())
                    .set(Tickets::getClosedAt, closedAt));
            if (update != 1) {
                throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
            }
            ticket.setStatus(TicketStatusEnum.CLOSED.name());
            ticket.setClosedAt(closedAt);

            // 保存状态历史
            if (!saveTicketStatusHistory(ticket, oldStatus, operatorId, closeReason, "CLOSE", closedAt)) {
                throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
            }
            // 保存操作日志
            if (!saveTicketOperationLog(ticket, "CLOSE", operatorId, operatorRole, clientIp,
                    "关闭工单，原因：" + closeReason, closedAt)) {
                throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
            }
            closedTicketIds.add(ticketId);
        }
        return closedTicketIds;
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
