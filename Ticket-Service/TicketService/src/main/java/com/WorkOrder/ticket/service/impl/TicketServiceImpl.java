package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.dto.*;
import com.WorkOrder.ticket.enums.TicketStatusEnum;
import com.WorkOrder.ticket.mapper.TicketCategoryMapper;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.model.Tickets;
import com.WorkOrder.ticket.service.TicketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务实现类
 */
@RequiredArgsConstructor
@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Tickets> implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketCategoryMapper categoryMapper;
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketOperationLogMapper ticketOperationLogMapper;


    /**
     * 创建工单
     * @param creatorId 创建者ID
     * @param creatorName 创建者名称
     * @param createTicketDto 创建工单DTO
     * @return 工单响应对象
     */
    @Transactional
    @Override
    public TicketResponse createTicket(Long creatorId, String creatorName, CreateTicketDto createTicketDto) {
        TicketResponse ticketResponse = new TicketResponse(); // 创建工单响应对象
        TicketCategory ticketCategory = categoryMapper.selectById(createTicketDto.getCategoryId()); // 根据类别ID获取类别信息
        LocalDateTime now = LocalDateTime.now(); // 获取当前时间

        if (createTicketDto.getPriority() == 0|| createTicketDto.getPriority() > 4){
            int defaultPriority = ticketCategory.getDefaultPriority();
            createTicketDto.setPriority(defaultPriority);
        }

        Tickets ticket = new Tickets();
        BeanUtils.copyProperties(createTicketDto, ticket);
        ticket.setCreatorId(creatorId); // 设置创建者ID

        //todo 生成工单编号，现在暂时使用随机数模拟
        String ticketNo = String.valueOf((int) (Math.random() * 10000000));
        ticket.setTicketNo(ticketNo); // 设置工单编号

        ticketResponse.setCategoryName(ticketCategory.getName()); // 设置工单类别名称
        ticketResponse.setCreatorName(creatorName);
        ticketResponse.setHandlerId(null); // 设置处理者ID为null
        ticketResponse.setHandlerName(null); // 设置处理者名称为null
        ticketResponse.setAssignedAt(null); // 设置分配时间为空

        ticket.setStatus(TicketStatusEnum.PENDING_ASSIGN.name()); // 设置工单状态为待分配
        ticket.setResponseDeadline(
                now.plusMinutes(ticketCategory.getDefaultResponseSla())
        ); // 设置响应截止时间

        ticket.setResolutionDeadline(
                now.plusMinutes(ticketCategory.getDefaultResolutionSla())
        ); // 设置解决截止时间

        ticket.setFirstResponseAt(null); // 设置首次响应时间为null
        ticket.setResolvedAt(null); // 设置解决时间为null
        ticket.setClosedAt(null); // 设置关闭时间为null
        ticket.setSlaStatus("NORMAL"); // 设置SLA状态为正常
        ticket.setEscalatedLevel(0); // 设置升级级别为0
        ticket.setRemindCount(0); // 设置催办次数为0
        ticket.setSource("WEB"); // 设置来源为WEB

        int insert = ticketMapper.insert(ticket);
        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.TICKET_CREATE_FAILED);
        }

        /**
         * 创建工单状态历史记录
         */
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicketId(ticket.getId());
        history.setFromStatus(null);
        history.setToStatus(TicketStatusEnum.PENDING_ASSIGN.name());
        history.setEvent("CREATE");
        history.setOperatorId(creatorId);
        history.setRemark("用户创建工单");
        // 插入工单状态历史记录
        ticketStatusHistoryMapper.insert(history);


        Tickets newTicket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Tickets>()
                        .eq(Tickets::getTicketNo, ticketNo));
        if (newTicket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_CREATE_FAILED);
        }

        return TicketConverter.toResponse(newTicket, ticketResponse);
    }

    /**
     * 根据用户ID和状态查询工单列表
     * @param userId 用户ID
     * @param myTicketPageDto 工单分页查询DTO
     * @return 工单响应对象列表
     */
    @Override
    public List<TicketResponse> myTickets(Long userId, MyTicketPageDto myTicketPageDto) {
        // 根据用户ID和状态查询工单列表
        List<Tickets> myTickets = null;
        if (myTicketPageDto.getStatus().equals("all") || myTicketPageDto.getStatus().isEmpty()) {
            myTickets = ticketMapper.selectList(
                    new LambdaQueryWrapper<Tickets>()
                            .eq(Tickets::getCreatorId, userId)
                            .orderByDesc(Tickets::getUpdatedAt)
                            .orderByDesc(Tickets::getId)
                            .last("limit " + myTicketPageDto.getPageSize()));
        } else {
            myTickets = ticketMapper.selectList(
                    new LambdaQueryWrapper<Tickets>()
                            .eq(Tickets::getCreatorId, userId)
                            .eq(Tickets::getStatus, myTicketPageDto.getStatus())
                            .orderByDesc(Tickets::getUpdatedAt)
                            .orderByDesc(Tickets::getId)
                            .last("limit " + myTicketPageDto.getPageSize()));
        }
        if (myTickets == null || myTickets.isEmpty()){
            return Collections.emptyList();
        }

        // 构建工单响应对象列表
        List<TicketResponse> ticketResponses = myTickets.stream()
                .map(TicketConverter::toResponse)
                .collect(Collectors.toList());

        return ticketResponses;
    }

    /**
     * 获取工单历史统计信息
     * @param userId 用户ID
     * @return 工单历史统计信息
     */
    @Override
    public TicketHistoryStatisticsDto getTicketHistoryStatistics(Long userId) {
        TicketHistoryStatisticsDto statistics =
                ticketMapper.selectHistoryStatistics(userId);

        List<MonthlyCountDto> byMonth =
                ticketMapper.selectMonthlyStatistics(userId);

        statistics.setByMonth(byMonth);
        return statistics;
    }

    /**
     * 根据工单ID获取工单信息
     * @param ticketId 工单ID
      * @return 工单响应对象
     */
    @Override
    public TicketResponse getTicketInfo(Long ticketId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        return TicketConverter.toResponse(ticket);
    }

    /**
     * 回复工单
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param ticketReplyDto 回复工单的DTO
     * @return 是否成功
     */
    @Override
    public Boolean ticketReplyInfo(Long ticketId, Long userId, String operatorRole, TicketReplyDto ticketReplyDto) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null){
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        Long operatorId = userId;
        // 操作类型
        String action;
        // 接收者ID
        Long receiverId;
        // 工单中的业务身份决定回复类型，登录角色独立保存到日志。
        if (Objects.equals(operatorId, ticket.getCreatorId())) {
            action = "USER_REPLY";
            receiverId = ticket.getHandlerId();
        } else if (Objects.equals(operatorId, ticket.getHandlerId())) {
            action = "HANDLER_REPLY";
            receiverId = ticket.getCreatorId();
        } else if ("ADMIN".equals(operatorRole)) {
            // 管理员属于客服侧回复
            action = "HANDLER_REPLY";
            receiverId = ticket.getCreatorId();
        } else {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 创建工单操作日志
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticketId);
        log.setAction(action);
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorRole);
        log.setContent(ticketReplyDto.getContent());
        // 插入工单操作日志
        int insert = ticketOperationLogMapper.insert(log);

        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        //todo 发送通知，通知模块还没实现
//        if (receiverId != null) {
//            notificationService.send(
//                    receiverId,
//                    "工单有新回复",
//                    ticketReplyDto.getContent()
//            );
//        }


        return true;
    }

    /**
     * 催办工单
     * @param ticketId 工单ID
     * @param userId 用户ID
      * @return 是否成功
     */
    @Override
    public Boolean ticketExpedite(Long ticketId, Long userId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        if (!Objects.equals(ticket.getCreatorId(), userId)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (ticket.getRemindCount() >= 3) {
            throw new SystemException(SystemExceptionEnum.TICKET_ALREADY_ESCALATED);
        }

        if (ticketMapper.incrementRemindCount(ticketId, userId) != 1) {
            // 并发请求可能已用完最后一次催办机会，重新查询以返回准确的错误。
            Tickets latestTicket = ticketMapper.selectById(ticketId);
            if (latestTicket == null) {
                throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
            }
            if (latestTicket.getRemindCount() >= 3) {
                throw new SystemException(SystemExceptionEnum.TICKET_ALREADY_ESCALATED);
            }
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_UPDATE_ERROR);
        }
        //todo 操作日志及消息通知待接入；催办不修改升级级别或 SLA 状态。
        return true;
    }

    /**
     * 撤销工单
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 工单响应对象
     */
    @Override
    public TicketResponse cancelTicket(Long ticketId, Long userId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        String ticketStatus = ticket.getStatus();
        if (
                !ticketStatus.equals(TicketStatusEnum.PENDING_ASSIGN.name()) &&
                !ticketStatus.equals(TicketStatusEnum.PENDING_RESPONSE.name()) &&
                !ticketStatus.equals(TicketStatusEnum.PROCESSING.name())){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        } else {
            ticket.setStatus(TicketStatusEnum.CANCELLED.name());
            ticketMapper.updateById(ticket);
        }

        TicketResponse ticketResponse = TicketConverter.toResponse(ticket);

        return ticketResponse;
    }

    /**
     * 确认工单
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 工单响应对象
     */
    @Override
    public TicketResponse confirmTicket(Long ticketId, Long userId) {
        Tickets ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        if(!ticket.getStatus().equals(TicketStatusEnum.RESOLVED.name())){
            throw new SystemException(SystemExceptionEnum.TICKET_STATUS_NOT_ALLOWED);
        } else {
            ticket.setStatus(TicketStatusEnum.CLOSED.name());
            ticket.setClosedAt(LocalDateTime.now());
            int update = ticketMapper.updateById(ticket);
            if(update < 1)
                throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
            // todo 确认后用消息模块通知处理人handler
        }
        return TicketConverter.toResponse(ticket);
    }
}
