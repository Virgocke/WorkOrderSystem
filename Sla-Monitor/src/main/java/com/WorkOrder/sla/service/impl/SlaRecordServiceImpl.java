package com.WorkOrder.sla.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.sla.dto.SlaRecordBoardDto;
import com.WorkOrder.sla.feignclient.TicketFeignClient;
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

    private final TicketFeignClient ticketFeignClient;


    /**
     * 获取实时SLA记录
     *
     * @param slaRecordBoardDto SLA记录看板DTO
     * @return SLA记录分页结果
     */
    @Override
    public PageResult<TicketResponse> getRealTimeSlaRecords(SlaRecordBoardDto slaRecordBoardDto) {
        Result<PageResult<TicketResponse>> ticketResult =
                ticketFeignClient.getTicketsBySlaStatus(
                        slaRecordBoardDto.getPage(),
                        slaRecordBoardDto.getPageSize(),
                        normalize(slaRecordBoardDto.getSlaStatus()),
                        normalize(slaRecordBoardDto.getStatus()));
        if (ticketResult == null || ticketResult.getCode() != 0 || ticketResult.getData() == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        return ticketResult.getData();
    }

    /**
     * 去掉筛选值两端空白，空值或纯空白转换为 null。
     *
     * @param value 原始筛选值
     * @return 规范化后的筛选值；原值为空或纯空白时为 null
     */
    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
