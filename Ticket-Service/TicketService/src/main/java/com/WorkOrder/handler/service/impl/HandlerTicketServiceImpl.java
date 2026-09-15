package com.WorkOrder.handler.service.impl;

import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.service.HandlerTicketService;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.List;
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

    // 时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 格式化时间
     * @param time 时间
     * @return 格式化后的时间
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }


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
                .map(ticket1 -> buildTicketResponse(ticket1, new TicketResponse()))
                .collect(Collectors.toList());
    }

    /**
     * 转换工单响应对象，使时间类型正确
     * @param ticket 工单
     * @param ticketResponse 工单响应对象
     * @return 工单响应对象
     */
    private TicketResponse buildTicketResponse(Tickets ticket, TicketResponse ticketResponse) {
        // 复制工单属性到工单响应对象
        BeanUtils.copyProperties(
            ticket,
            ticketResponse,
            "responseDeadline",
            "resolutionDeadline",
            "firstResponseAt",
            "resolvedAt",
            "closedAt"
        );

        // 设置响应截止时间，解决截止时间，首次响应时间，解决时间和关闭时间
        ticketResponse.setResponseDeadline(
                formatTime(ticket.getResponseDeadline())
        );
        ticketResponse.setResolutionDeadline(
                formatTime(ticket.getResolutionDeadline())
        );
        ticketResponse.setFirstResponseAt(
                formatTime(ticket.getFirstResponseAt())
        );
        ticketResponse.setResolvedAt(
                formatTime(ticket.getResolvedAt())
        );
        ticketResponse.setClosedAt(
                formatTime(ticket.getClosedAt())
        );
        return ticketResponse;
    }
}
