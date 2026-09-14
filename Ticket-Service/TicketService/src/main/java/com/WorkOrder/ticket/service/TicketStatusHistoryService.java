package com.WorkOrder.ticket.service;

import com.WorkOrder.model.StatusHistory;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月15日 03:25
 * @description 工单状态历史记录服务
 */
public interface TicketStatusHistoryService {
    /**
     * 获取工单状态时间线
     * @param ticketId 工单ID
     * @return 工单状态时间线
     */
    List<StatusHistory> getTicketStatusTimeline(Long ticketId);
}
