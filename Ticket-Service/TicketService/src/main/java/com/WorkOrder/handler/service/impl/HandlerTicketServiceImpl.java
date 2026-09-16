package com.WorkOrder.handler.service.impl;

import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.service.HandlerTicketService;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
