package com.WorkOrder.sla.service.impl;

import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.sla.dto.SlaRecordBoardDto;
import com.WorkOrder.sla.mapper.SlaRecordMapper;
import com.WorkOrder.sla.service.SlaRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Virgor
 * @date 2026年09月19日 04:30
 * @description SlaRecordServiceImpl 工单SLA记录服务实现类
 */
@Service
@RequiredArgsConstructor
public class SlaRecordServiceImpl implements SlaRecordService {

    private final SlaRecordMapper slaRecordMapper;


    @Override
    public PageResult<TicketResponse> getRealTimeSlaRecords(Long operatorId, String operatorRole, SlaRecordBoardDto slaRecordBoardDto) {
        return null;
    }
}
