package com.WorkOrder.admin.service;

import com.WorkOrder.handler.dto.AdminTicketListDto;
import com.WorkOrder.handler.dto.AssignTicketDto;
import com.WorkOrder.model.ticket.TicketResponse;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月17日 01:54
 * @description 管理员工单服务
 */
public interface AdminTicketService {
    List<TicketResponse> getTicketListForAdmin(Long adminId, @Valid AdminTicketListDto adminTicketListDto);

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
}
