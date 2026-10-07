package com.WorkOrder.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 数据库每日趋势查询结果。
 */
@Data
public class DashboardTrendRow {

    /**
     * 趋势统计日期。
     */
    private LocalDate statDate;

    /**
     * 当日创建的工单数量。
     */
    private long created;

    /**
     * 当日解决的工单数量。
     */
    private long resolved;
}
