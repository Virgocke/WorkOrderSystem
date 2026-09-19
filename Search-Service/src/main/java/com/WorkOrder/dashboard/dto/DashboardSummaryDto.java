package com.WorkOrder.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 仪表盘汇总查询结果。 */
@Data
public class DashboardSummaryDto {

    /** 系统中的工单总数。 */
    private long totalTickets;

    /** 待分配与待响应工单数量之和。 */
    private long pendingCount;

    /** 已关闭工单的 SLA 达成率，单位为百分比。 */
    private BigDecimal slaComplianceRate;

    /** 从创建到首次响应的平均时长，单位为分钟。 */
    private BigDecimal avgResponseMinutes;

    /** 从创建到解决的平均时长，单位为分钟。 */
    private BigDecimal avgResolutionMinutes;
}
