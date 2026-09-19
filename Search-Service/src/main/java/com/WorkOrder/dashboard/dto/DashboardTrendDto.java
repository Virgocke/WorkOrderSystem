package com.WorkOrder.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 仪表盘每日工单趋势。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendDto {

    /** 趋势日期，格式为 MM-dd。 */
    private String date;

    /** 当日创建的工单数量。 */
    private long created;

    /** 当日解决的工单数量。 */
    private long resolved;
}
