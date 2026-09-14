package com.WorkOrder.ticket.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月15日 02:09
 * @description 工单历史统计信息DTO
 */
@Data
public class TicketHistoryStatisticsDto {
    private int total;
    private int processing;
    private int resolved;
    private int closed;
    private int cancelled;
    private BigDecimal avgFirstResponseHours;
    private int last30d;
    private List<MonthlyCountDto> byMonth;
}
