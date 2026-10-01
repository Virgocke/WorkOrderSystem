package com.WorkOrder.dashboard.controller;

import com.WorkOrder.dashboard.dto.*;
import com.WorkOrder.dashboard.service.AuditService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员全局审计，只提供查询入口。 */
@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {
    /** 管理员审计查询服务。 */
    private final AuditService service;

    /** 管理员查询跨工单的操作日志和状态变更历史。 */
    @GetMapping
    public Result<PageResult<TicketAuditItem>> tickets(AuditQuery query) {
        return Result.success(service.tickets(query));
    }

    /** 管理员查询配置修改记录及前后值。 */
    @GetMapping("/configurations")
    public Result<PageResult<ConfigurationAuditItem>> configurations(AuditQuery query) {
        return Result.success(service.configurations(query));
    }
}
