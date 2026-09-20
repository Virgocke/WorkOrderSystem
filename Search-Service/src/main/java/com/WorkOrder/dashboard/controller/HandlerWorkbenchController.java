package com.WorkOrder.dashboard.controller;

import com.WorkOrder.dashboard.dto.HandlerWorkbenchDto;
import com.WorkOrder.dashboard.service.DashboardService;
import com.WorkOrder.model.Result;
import com.WorkOrder.security.CurrentUserIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 处理人工作台控制器。 */
@RestController
@RequestMapping("/handler")
@RequiredArgsConstructor
public class HandlerWorkbenchController {

    /** 仪表盘与工作台服务。 */
    private final DashboardService dashboardService;

    /** 当前登录用户 ID 解析组件。 */
    private final CurrentUserIdProvider currentUserIdProvider;

    /**
     * 查询当前处理人的工作台。
     *
     * @param authentication 当前 OAuth2 认证信息
     * @return 当前处理人的指标、状态分布、临期工单和档案
     */
    @GetMapping("/workbench")
    @PreAuthorize("hasRole('HANDLER')")
    public Result<HandlerWorkbenchDto> getWorkbench(Authentication authentication) {
        Long handlerId = currentUserIdProvider.get(authentication);
        return Result.success(dashboardService.getHandlerWorkbench(handlerId));
    }
}
