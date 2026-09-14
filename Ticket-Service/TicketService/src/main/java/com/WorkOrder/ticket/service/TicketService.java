package com.WorkOrder.ticket.service;

import com.WorkOrder.model.TicketResponse;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.dto.MyTicketPageDto;
import com.WorkOrder.ticket.dto.TicketHistoryStatisticsDto;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务接口
 */
public interface TicketService extends IService<Tickets> {
    /**
     * 创建工单
     * @param creatorId 创建者ID
     * @param creatorName 创建者名称
     * @param createTicketDto 创建工单的DTO
     * @return
     */
    TicketResponse createTicket(Long creatorId,String creatorName, CreateTicketDto createTicketDto);

    /**
     * 获取用户工单列表
      * @param userId 用户ID
      * @param myTicketPageDto 工单分页查询DTO
     * @return 工单列表
     */
    List<TicketResponse> myTickets(Long userId, MyTicketPageDto myTicketPageDto);

    TicketHistoryStatisticsDto getTicketHistoryStatistics(Long userId);
}
