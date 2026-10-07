package com.WorkOrder.dashboard.controller;

import com.WorkOrder.dashboard.dto.DashboardOverviewDto;
import com.WorkOrder.dashboard.service.DashboardService;
import com.WorkOrder.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理端全局仪表盘控制器。
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    /**
     * 管理端全局仪表盘服务。
     */
    private final DashboardService dashboardService;

    /**
     * 查询管理端全局仪表盘。
     *
     * @return 包含全局仪表盘数据的统一响应
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DashboardOverviewDto> getOverview() {
        return Result.success(dashboardService.getOverview());
    }
}
