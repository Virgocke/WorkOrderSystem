package com.WorkOrder.user.dto;

import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月13日 00:29
 * @description 按月统计工单数量DTO
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