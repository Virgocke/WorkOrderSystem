package com.WorkOrder.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

/** 数据库处理人每日负载查询结果。 */
@Data
public class DashboardHandlerHeatRow {

    /** 处理人用户 ID。 */
    private Long handlerId;

    /** 处理人显示名称。 */
    private String handlerName;

    /** 负载统计日期。 */
    private LocalDate statDate;

    /** 该处理人在统计日期的在办或办结工单数量。 */
    private long count;
}
