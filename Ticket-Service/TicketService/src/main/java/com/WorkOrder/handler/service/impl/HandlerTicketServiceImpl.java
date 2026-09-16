package com.WorkOrder.handler.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.handler.dto.TransferTicketDto;
import com.WorkOrder.handler.mapper.AssignmentRecordMapper;
import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.handler.service.HandlerTicketService;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月16日 03:27
 * @description 处理人服务实现类
 */
@Service
@RequiredArgsConstructor
public class HandlerTicketServiceImpl implements HandlerTicketService {

    private final HandlerProfileMapper handlerProfileMapper;
    private final TicketMapper ticketMapper;
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketOperationLogMapper ticketOperationLogMapper;
    private final AssignmentRecordMapper assignmentRecordMapper;
    private final UserFeignClient userFeignClient;

    /**
     * 获取处理人工单列表
     * @param handlerId 处理人ID
     * @param handlerTicketPageDto 分页参数
     * @return 处理人工单列表
     */
    @Override
    public List<TicketResponse> getHandlerTicket(Long handlerId, HandlerTicketPageDto handlerTicketPageDto) {
        List<Tickets> ticketList = null;

        Page<Tickets> page = new Page<>(handlerTicketPageDto.getPage(), handlerTicketPageDto.getPageSize());

        LambdaQueryWrapper<Tickets> queryWrapper = new LambdaQueryWrapper<Tickets>();
        queryWrapper.eq(Tickets::getHandlerId, handlerId);
        // 如果传了状态参数，则添加查询条件
        if (!handlerTicketPageDto.getStatus().equals("") && !handlerTicketPageDto.getStatus().equals("all")){
            queryWrapper.eq(Tickets::getStatus, handlerTicketPageDto.getStatus());
        }
        //todo keyword要用search模块查询，这里先不写

        // 根据排序参数添加查询条件
        if (handlerTicketPageDto.getSort().equals("deadline")){
            queryWrapper.orderByAsc(Tickets::getResponseDeadline);
        } else if (handlerTicketPageDto.getSort().equals("priority")) {
            queryWrapper.orderByDesc(Tickets::getPriority);
        } else if (handlerTicketPageDto.getSort().equals("createdAt")) {
            queryWrapper.orderByDesc(Tickets::getCreatedAt);
        }

        ticketList = ticketMapper.selectPage(page, queryWrapper).getRecords();

        return ticketList
                .stream()
                .map(TicketConverter::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 处理人首次响应
     * @param ticketId 工单ID
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 工单响应结果
     */
    @Override
    @Transactional
    public TicketResponse firstResponse(Long ticketId, Long operatorId, String operatorRole, String clientIp) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        // 如果工单不存在，则抛出异常
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 如果当前登录用户不是管理员，且不是处理人，则抛出异常
        boolean isAdmin = "ADMIN".equals(operatorRole);
        if (!isAdmin && (!"HANDLER".equals(operatorRole)
                || !Objects.equals(operatorId, ticket.getHandlerId()))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }


        // 如果工单状态不是待响应，则抛出异常
        if (!TicketStatusEnum.PENDING_RESPONSE.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }


        // 保存工单状态历史
        String oldStatus = ticket.getStatus();
        // 设置新的工单状态
        ticket.setStatus(TicketStatusEnum.PROCESSING.name());
        ticket.setFirstResponseAt(LocalDateTime.now());

        // 仅首次响应可以变更状态，避免重复请求产生重复日志。
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, oldStatus)
                .eq(!isAdmin, Tickets::getHandlerId, operatorId)
                .set(Tickets::getStatus, ticket.getStatus())
                .set(Tickets::getFirstResponseAt, ticket.getFirstResponseAt()));
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(ticket, oldStatus, operatorId, "首次响应","RESPOND");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        if (!saveTicketOperationLog(
                ticket,
                "RESPOND",
                operatorId,
                operatorRole,
                clientIp,
                "首次响应工单，开始处理")) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        return TicketConverter.toResponse(ticket);
    }

    /**
     * 处理人解决工单
     * @param ticketId 工单ID
     * @param solution 解决方案
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 工单解决结果
     */
    @Override
    @Transactional
    public TicketResponse resolveTicket(
            Long ticketId,
            String solution,
            Long operatorId,
            String operatorRole,
            String clientIp) {

        Tickets ticket = ticketMapper.selectById(ticketId);

        // 如果工单不存在，则抛出异常
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 保存工单状态历史
        String oldStatus = ticket.getStatus();

        // 如果当前登录用户不是管理员，且不是处理人，则抛出异常
        boolean isAdmin = "ADMIN".equals(operatorRole);
        if (!isAdmin && (!"HANDLER".equals(operatorRole)
                || !Objects.equals(operatorId, ticket.getHandlerId()))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 如果工单状态不是处理中，则抛出异常
        if (!TicketStatusEnum.PROCESSING.name().equals(ticket.getStatus())) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        }

        // 设置新的工单状态
        ticket.setStatus(TicketStatusEnum.RESOLVED.name());

        // 仅首次响应可以变更状态，避免重复请求产生重复日志。
        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                .eq(Tickets::getStatus, oldStatus)
                .eq(!isAdmin, Tickets::getHandlerId, operatorId)
                .set(Tickets::getStatus, ticket.getStatus())
                .set(Tickets::getResolvedAt, LocalDateTime.now()));

        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(ticket, oldStatus, operatorId, solution, "RESOLVE");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        if (!saveTicketOperationLog(
                ticket,
                "RESOLVE",
                operatorId,
                operatorRole,
                clientIp,
                "解决工单")) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        return TicketConverter.toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse transferTicketToOtherHandler(
            Long ticketId,
            TransferTicketDto transferTicketDto,
            Long operatorId,
            String operatorRole,
            String clientIp) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 如果当前登录用户不是管理员，且不是处理人，则抛出异常
        boolean isAdmin = "ADMIN".equals(operatorRole);
        if (!isAdmin && (!"HANDLER".equals(operatorRole)
                || !Objects.equals(operatorId, ticket.getHandlerId()))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 取出旧处理人ID
        Long oldHandlerId = ticket.getHandlerId();
        // 如果旧处理人ID与新处理人ID相同，则抛出异常
        if (oldHandlerId.equals(transferTicketDto.getToHandlerId())){
            throw new SystemException(SystemExceptionEnum.TICKET_TRANSFER_SAME_HANDLER);
        }

        // 校验新处理人ID是否存在
        Result<UserProfile> result = userFeignClient.getById(transferTicketDto.getToHandlerId());
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        // 校验新处理人ID是否状态正常
        UserProfile toHandler = result.getData();
        if (toHandler.getRole() != 1 || toHandler.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        // 设置新的处理人ID
        ticket.setHandlerId(transferTicketDto.getToHandlerId());
        ticket.setAssignedAt(LocalDateTime.now());

        int update = ticketMapper.update(null, new LambdaUpdateWrapper<Tickets>()
                .eq(Tickets::getId, ticketId)
                // 只有当前处理人或管理员才能转交工单
                .eq(Tickets::getHandlerId, oldHandlerId)
                // 如果不是管理员，则需要检查处理人ID
                .eq(!isAdmin, Tickets::getHandlerId, operatorId)
                .set(Tickets::getHandlerId,transferTicketDto.getToHandlerId())
                .set(Tickets::getAssignedAt, ticket.getAssignedAt()));

        // 失败则抛出异常
        if (update != 1) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存工单状态历史
        boolean saveTicketStatusHistory =
                saveTicketStatusHistory(ticket,
                        ticket.getStatus(),
                        operatorId,
                        transferTicketDto.getReason(),
                        "TRANSFER");

        if (!saveTicketStatusHistory){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 与工单状态、状态历史在同一事务中保存操作日志。
        if (!saveTicketOperationLog(
                ticket,
                "TRANSFER",
                operatorId,
                operatorRole,
                clientIp,
                "转交工单")) {
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }

        // 保存转交记录
        AssignmentRecord assignmentRecord = new AssignmentRecord();
        assignmentRecord.setTicketId(ticketId);
        assignmentRecord.setHandlerId(transferTicketDto.getToHandlerId());


        //todo 分数计算模块还没完成，先手动设置为0
        assignmentRecord.setScore(BigDecimal.ZERO);
        assignmentRecord.setSkillMatchScore(BigDecimal.ZERO);
        assignmentRecord.setLoadScore(BigDecimal.ZERO);
        assignmentRecord.setSlaScore(BigDecimal.ZERO);
        assignmentRecord.setRatingScore(BigDecimal.ZERO);


        assignmentRecord.setAssignedBy("MANUAL");
        assignmentRecord.setCreatedAt(LocalDateTime.now());
        int insert = assignmentRecordMapper.insert(assignmentRecord);
        if (insert != 1) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        //todo 转交后要用消息模块通知被转交人

        return TicketConverter.toResponse(ticket);
    }

    /**
     * 保存工单状态历史
     * @param ticket 工单
     * @param oldStatus 旧状态
     * @param handlerId 处理人ID
     * @param remark 备注
     * @return 是否保存成功
     */
    private boolean saveTicketStatusHistory(
            Tickets ticket,
            String oldStatus,
            Long handlerId,
            String remark,
            String event) {
        TicketStatusHistory ticketStatusHistory = new TicketStatusHistory();
        ticketStatusHistory.setTicketId(ticket.getId());
        ticketStatusHistory.setFromStatus(oldStatus);
        ticketStatusHistory.setToStatus(ticket.getStatus());
        ticketStatusHistory.setEvent(event);
        ticketStatusHistory.setOperatorId(handlerId);
        ticketStatusHistory.setRemark(remark);
        int insert = ticketStatusHistoryMapper.insert(ticketStatusHistory);
        if (insert != 1) {
            return false;
        }
        return true;

    }

    /**
     * 保存工单操作日志
     * @param ticket 工单
     * @param action 操作
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @param content 内容
     * @return 是否保存成功
     */
    private boolean saveTicketOperationLog(
            Tickets ticket,
            String action,
            Long operatorId,
            String operatorRole,
            String clientIp,
            String content) {
        TicketOperationLog ticketOperationLog = new TicketOperationLog();
        ticketOperationLog.setTicketId(ticket.getId());
        ticketOperationLog.setAction(action);
        ticketOperationLog.setOperatorId(operatorId);
        ticketOperationLog.setOperatorRole(operatorRole);
        ticketOperationLog.setIpAddress(clientIp);
        ticketOperationLog.setContent(content);
        int insert = ticketOperationLogMapper.insert(ticketOperationLog);
        if (insert != 1) {
            return false;
        }
        return true;
    }
}
