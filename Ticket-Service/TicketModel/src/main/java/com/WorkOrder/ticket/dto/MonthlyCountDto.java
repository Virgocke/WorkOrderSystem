package com.WorkOrder.ticket.dto;

import lombok.Data;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 按月份汇总的工单数量。
 */
@Data
public class MonthlyCountDto {
    private String month;
    private Long count;
}