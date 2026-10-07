package com.WorkOrder.sla.service;

import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.sla.dto.SlaRecordBoardDto;

/**
 * @author Virgor
 * @date 2026年09月19日 04:29
 * @description 查询 SLA 看板上的实时工单与历史 SLA 记录。
 */
public interface SlaRecordService {
    /**
     * 获取实时SLA记录
     *
     * @param slaRecordBoardDto SLA记录看板DTO
     * @return PageResult<TicketResponse>
     */
    PageResult<TicketResponse> getRealTimeSlaRecords(SlaRecordBoardDto slaRecordBoardDto);
}
