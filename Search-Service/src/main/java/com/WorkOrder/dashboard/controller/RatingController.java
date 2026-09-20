package com.WorkOrder.dashboard.controller;

import com.WorkOrder.dashboard.dto.RatingDetailDto;
import com.WorkOrder.dashboard.service.DashboardService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端评价明细控制器。 */
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final DashboardService dashboardService;

    /**
     * 分页查询评价明细。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param handlerId 可选的处理人 ID
     * @return 评价明细分页结果
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<PageResult<RatingDetailDto>> getRatings(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "handlerId", required = false) Long handlerId) {
        return Result.success(dashboardService.getRatings(page, pageSize, handlerId));
    }
}
