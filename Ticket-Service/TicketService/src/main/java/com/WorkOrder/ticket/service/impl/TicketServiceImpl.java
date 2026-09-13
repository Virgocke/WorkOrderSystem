package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.model.TicketResponse;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.Tickets;
import com.WorkOrder.ticket.service.TicketService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务实现类
 */
@RequiredArgsConstructor
@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Tickets> implements TicketService {

    private final TicketMapper ticketMapper;

    @Override
    public TicketResponse createTicket(CreateTicketDto createTicketDto) {
        //todo 创建工单
        return null;
    }
}
