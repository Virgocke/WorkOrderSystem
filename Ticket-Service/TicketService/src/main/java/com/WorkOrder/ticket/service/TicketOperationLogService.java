package com.WorkOrder.ticket.service;

import com.WorkOrder.model.ticket.OperationLog;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月15日 20:06
 * @description 工单操作日志服务接口
 */
public interface TicketOperationLogService {
    /**
     * 根据工单ID获取工单操作日志列表
     * @param ticketId 工单ID
     * @return 工单操作日志列表
     */
    List<OperationLog> getTicketOperationLog(Long ticketId);
}
