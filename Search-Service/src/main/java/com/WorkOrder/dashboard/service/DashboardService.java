package com.WorkOrder.dashboard.service;

import com.WorkOrder.dashboard.dto.DashboardOverviewDto;

/** 管理端全局仪表盘服务。 */
public interface DashboardService {

    /**
     * 查询管理端全局仪表盘。
     *
     * @return 仪表盘汇总、分布、趋势、处理人负载和最新工单
     */
    DashboardOverviewDto getOverview();
}
