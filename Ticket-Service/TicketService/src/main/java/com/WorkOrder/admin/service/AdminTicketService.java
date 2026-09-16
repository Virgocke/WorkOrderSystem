package com.WorkOrder.admin.service;

import com.WorkOrder.handler.dto.AdminTicketListDto;
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
}
