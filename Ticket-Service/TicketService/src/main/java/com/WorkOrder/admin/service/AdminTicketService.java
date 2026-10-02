package com.WorkOrder.admin.service;

import com.WorkOrder.admin.dto.AdminTicketListDto;
import com.WorkOrder.admin.dto.CloseTicketDto;
import com.WorkOrder.handler.dto.AssignTicketDto;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月17日 01:54
 * @description 管理员工单服务
 */
public interface AdminTicketService {

    PageResult<TicketResponse> getTicketListForAdmin(Long adminId, @Valid AdminTicketListDto adminTicketListDto);

    /**
     * 手动分配无处理人或待分配的工单。
     *
     * @param ticketId 工单ID
     * @param assignTicketDto 分配参数
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 分配后的工单信息
     */
    TicketResponse assignTicket(Long ticketId, AssignTicketDto assignTicketDto,
                                Long operatorId, String operatorRole, String clientIp);

    /**
     * 批量分配无处理人或待分配的工单，任一工单分配失败时整批回滚。
     * @param ticketIds 工单ID列表
     * @param handlerId 目标处理人ID
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 分配成功的工单ID列表，去重并保持请求顺序
     */
    List<Long> assignTicketList(List<Long> ticketIds, Long handlerId,
                                Long operatorId, String operatorRole, String clientIp);

    /**
     * 强制关闭工单。
     * @param ticketId 工单ID
     * @param operatorId 当前操作人ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @param clientIp 客户端IP
     * @return 关闭后的工单信息
     */
    TicketResponse closeTicket(Long ticketId, CloseTicketDto closeTicketDto, Long operatorId, String operatorRole, String clientIp);

    /**
     * 批量关闭工单。
     * @param closedTickets 工单ID列表
     * @param reason 关闭原因
     * @param operatorId 操作人ID
     * @param operatorRole 操作人角色
     * @param clientIp 客户端IP
     * @return 关闭的工单ID列表
     */
    List<Long> closeTicketList(List<Long> closedTickets, String reason, Long operatorId, String operatorRole, String clientIp);
}
