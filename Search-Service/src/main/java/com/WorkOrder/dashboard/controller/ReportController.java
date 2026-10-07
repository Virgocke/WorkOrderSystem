package com.WorkOrder.dashboard.controller;

import com.WorkOrder.dashboard.dto.CategoryReportDto;
import com.WorkOrder.dashboard.dto.DashboardTrendDto;
import com.WorkOrder.dashboard.dto.HandlerReportPerformanceDto;
import com.WorkOrder.dashboard.service.DashboardService;
import com.WorkOrder.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月20日 19:17
 * @description 报表相关控制器
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    /**
     * 管理端全局仪表盘服务。
     */
    private final DashboardService dashboardService;

    /**
     * 获取处理人员报表性能数据。
     *
     * @return 处理人员报表性能数据列表
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/performance")
    public Result<List<HandlerReportPerformanceDto>> getHandlerReportPerformance() {
        return Result.success(dashboardService.getHandlerReportPerformance());
    }

    /**
     * 获取按顶级分类聚合的工单统计。
     *
     * @return 分类统计列表
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/category")
    public Result<List<CategoryReportDto>> getCategoryReport() {
        return Result.success(dashboardService.getCategoryReport());
    }


    /**
     * 获取工单趋势数据。
     *
     * @param days 趋势天数
     * @return 工单趋势数据列表
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/trend")
    public Result<List<DashboardTrendDto>> getTicketTrend(@RequestParam(value = "days", defaultValue = "30") int days) {
        return Result.success(dashboardService.getTicketTrend(days));
    }
}
