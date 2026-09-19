package com.WorkOrder.dashboard.dto;

import com.WorkOrder.model.ticket.TicketResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 管理端全局仪表盘响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDto {

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

    /** 按工单状态统计的数量分布。 */
    private List<DashboardCountDto> byStatus;

    /** 按工单优先级统计的数量分布。 */
    private List<DashboardCountDto> byPriority;

    /** 近十四天的工单创建与解决趋势。 */
    private List<DashboardTrendDto> trend;

    /** 处理人近七天负载三元组，元素依次为处理人、日期和数量。 */
    private List<List<Object>> handlerHeat;

    /** 最近更新的未终结工单，最多八条。 */
    private List<TicketResponse> recentTickets;
}
