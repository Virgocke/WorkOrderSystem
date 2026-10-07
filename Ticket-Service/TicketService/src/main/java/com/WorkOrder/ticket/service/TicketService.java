package com.WorkOrder.ticket.service;

import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.dto.MyTicketPageDto;
import com.WorkOrder.ticket.dto.TicketHistoryStatisticsDto;
import com.WorkOrder.ticket.dto.TicketReplyDto;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务接口
 */
public interface TicketService extends IService<Tickets> {
    /**
     * 创建工单
     *
     * @param creatorId 创建者ID
     * @param creatorName 创建者名称
     * @param createTicketDto 创建工单的DTO
     * @return 工单详情
     */
    TicketResponse createTicket(Long creatorId,String creatorName, CreateTicketDto createTicketDto);

    /**
     * 获取用户工单列表
     *
     * @param userId 用户ID
     * @param myTicketPageDto 工单分页查询DTO
     * @return 工单列表
     */
    List<TicketResponse> myTickets(Long userId, MyTicketPageDto myTicketPageDto);

    /**
     * 获取用户工单历史统计信息
     *
     * @param userId 用户ID
     * @return 工单历史统计信息
     */
    TicketHistoryStatisticsDto getTicketHistoryStatistics(Long userId);

    /**
     * 获取工单信息
     *
     * @param ticketId 工单ID
     * @return 工单信息
     */
    TicketResponse getTicketInfo(Long ticketId);

    /**
     * 回复工单信息
     *
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param ticketReplyDto 回复工单的DTO
     * @return 是否成功回复工单
     */
    Boolean ticketReplyInfo(Long ticketId, Long userId, String operatorRole, @Valid TicketReplyDto ticketReplyDto);

    /**
     * 催办工单
     *
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @return 是否成功催办工单
     */
    Boolean ticketExpedite(Long ticketId, Long userId, String clientIp);

    /**
     * 取消工单
     *
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 取消工单信息
     */
    TicketResponse cancelTicket(Long ticketId, Long userId);

    /**
     * 确认工单
     *
     * @param ticketId 工单ID
     * @param userId 用户ID
     * @return 确认工单信息
     */
    TicketResponse confirmTicket(Long ticketId, Long userId);

    /**
     * 根据SLA状态获取工单信息
     *
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前操作人角色
     * @param page 页码
     * @param pageSize 每页条数
     * @param slaStatus SLA状态
     * @param status 工单状态
     * @return 工单信息
     */
    PageResult<TicketResponse> getTicketsBySlaStatus(
            Long operatorId,
            String operatorRole,
            Long page,
            Long pageSize,
            String slaStatus,
            String status);
}
