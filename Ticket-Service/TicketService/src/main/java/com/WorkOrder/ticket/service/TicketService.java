package com.WorkOrder.ticket.service;

import com.WorkOrder.model.TicketResponse;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.validation.Valid;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务接口
 */
public interface TicketService extends IService<Tickets> {
    TicketResponse createTicket(CreateTicketDto createTicketDto);
}
