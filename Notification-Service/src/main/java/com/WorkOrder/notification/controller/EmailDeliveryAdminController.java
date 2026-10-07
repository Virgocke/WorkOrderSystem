package com.WorkOrder.notification.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.EmailDeliveryAdminQuery;
import com.WorkOrder.notification.dto.EmailDeliveryAdminResponse;
import com.WorkOrder.notification.dto.EmailDeliveryRetryLogResponse;
import com.WorkOrder.notification.dto.EmailDeliveryRetryRequest;
import com.WorkOrder.notification.dto.EmailDeliveryRetryResponse;
import com.WorkOrder.notification.service.EmailDeliveryAdminService;
import com.WorkOrder.security.CurrentUserIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员邮件任务管理，网关统一提供 /api 前缀。
 */
@RestController
@RequestMapping("/notifications/email-deliveries")
@PreAuthorize("hasRole('ADMIN')")
public class EmailDeliveryAdminController {
    private final EmailDeliveryAdminService adminService;
    private final CurrentUserIdProvider currentUserIdProvider;

    /**
     * 注入管理服务与既有 JWT 用户主键解析工具。
     *
     * @param adminService 邮件投递任务管理员服务
     * @param currentUserIdProvider 当前用户ID提供器
     */
    public EmailDeliveryAdminController(EmailDeliveryAdminService adminService, CurrentUserIdProvider currentUserIdProvider) {
        this.adminService = adminService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    /**
     * 默认只查看 FAILED，可按创建时间与投递状态筛选。
     *
     * @param authentication 携带管理员角色及用户 ID 的已认证 JWT 信息
     * @param query 待校验的投递状态、创建时间范围及分页条件
     * @return 统一响应，包含邮件任务公开字段及总数的分页结果，默认仅 FAILED
     */
    @GetMapping
    public Result<PageResult<EmailDeliveryAdminResponse>> list(Authentication authentication,
                                                               @Valid EmailDeliveryAdminQuery query) {
        return Result.success(adminService.list(currentUserIdProvider.get(authentication), query));
    }

    /**
     * 返回原地址供管理员确认，不公开邮件正文和租约令牌。
     *
     * @param authentication 携带管理员角色及用户 ID 的已认证 JWT 信息
     * @param id 邮件任务主键
     * @return 统一响应，包含邮件任务公开详情及原收件地址，不含正文和工作者令牌
     */
    @GetMapping("/{id}")
    public Result<EmailDeliveryAdminResponse> detail(Authentication authentication, @PathVariable("id") Long id) {
        return Result.success(adminService.detail(currentUserIdProvider.get(authentication), id));
    }

    /**
     * 按重发轮次逆序分页读取专用审计。
     *
     * @param authentication 携带管理员角色及用户 ID 的已认证 JWT 信息
     * @param id 邮件任务主键
     * @param query 重发审计的页码和每页条数
     * @return 统一响应，包含按接受轮次逆序的人工重发审计分页结果
     */
    @GetMapping("/{id}/retry-logs")
    public Result<PageResult<EmailDeliveryRetryLogResponse>> logs(Authentication authentication,
            @PathVariable("id") Long id, @Valid EmailDeliveryAdminQuery query) {
        return Result.success(adminService.logs(currentUserIdProvider.get(authentication), id, query));
    }

    /**
     * 管理员只确认重新排队，实际发送由现有邮件工作者完成。
     *
     * @param authentication 携带管理员角色及用户 ID 的已认证 JWT 信息
     * @param id 邮件任务主键
     * @param request 包含幂等请求 ID、预期轮次及重发原因的请求
     * @return 统一响应，包含已接受轮次、当前状态及工作者启用状态；成功仅确认排队，重复请求返回原接受轮次
     */
    @PostMapping("/{id}/retry")
    public Result<EmailDeliveryRetryResponse> retry(Authentication authentication, @PathVariable("id") Long id,
                                                    @Valid @RequestBody EmailDeliveryRetryRequest request) {
        return Result.success(adminService.retry(currentUserIdProvider.get(authentication), id, request));
    }
}
