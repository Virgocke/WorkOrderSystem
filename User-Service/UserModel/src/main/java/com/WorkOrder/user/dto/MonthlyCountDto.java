package com.WorkOrder.user.dto;

import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月13日 00:29
 * @description 按月份汇总的工单数量。
 */
@Data
public class MonthlyCountDto {

    /**
     * 月份，格式：yyyy-MM
     */
    private String month;

    /**
     * 当月工单数量
     */
    private Long count;
}