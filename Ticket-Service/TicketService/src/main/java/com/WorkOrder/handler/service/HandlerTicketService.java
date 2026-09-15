package com.WorkOrder.handler.service;

import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.model.ticket.TicketResponse;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 03:22
 * @description
 */
public interface HandlerTicketService {
    /**
     * 根据处理人ID获取工单列表
      * @param handlerId 处理人ID
     * @param handlerTicketPageDto 分页参数
     * @return 工单列表
     */
    List<TicketResponse> getHandlerTicket(Long handlerId, HandlerTicketPageDto handlerTicketPageDto);
}
