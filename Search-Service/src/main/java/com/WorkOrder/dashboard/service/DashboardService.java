package com.WorkOrder.dashboard.service;

import com.WorkOrder.dashboard.dto.CategoryReportDto;
import com.WorkOrder.dashboard.dto.DashboardOverviewDto;
import com.WorkOrder.dashboard.dto.HandlerReportPerformanceDto;
import com.WorkOrder.dashboard.dto.HandlerWorkbenchDto;

import java.util.List;

/** 管理端全局仪表盘服务。 */
public interface DashboardService {

    /**
     * 查询管理端全局仪表盘。
     *
     * @return 仪表盘汇总、分布、趋势、处理人负载和最新工单
     */
    DashboardOverviewDto getOverview();

    /**
     * 查询指定处理人的工作台。
     *
     * @param handlerId 当前处理人的用户 ID
     * @return 处理人指标、状态分布、临期工单和档案
     */
    HandlerWorkbenchDto getHandlerWorkbench(Long handlerId);

    /**
     * 查询处理人报表性能。
     *
     * @return 处理人报表性能
     */
    List<HandlerReportPerformanceDto> getHandlerReportPerformance();

    /**
     * 查询按顶级分类聚合的工单统计。
     *
     * @return 分类统计列表
     */
    List<CategoryReportDto> getCategoryReport();

}
