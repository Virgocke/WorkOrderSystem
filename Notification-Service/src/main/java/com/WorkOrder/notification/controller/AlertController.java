package com.WorkOrder.notification.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.notification.AlertRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.AlertDto;
import com.WorkOrder.notification.service.AlertService;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.security.CurrentUserRoleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Virgor
 * @date 2026年09月19日 00:09
 * @description
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/alerts")
public class AlertController {

    private final CurrentUserIdProvider currentUserIdProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;
    private final AlertService alertService;


    /**
     * 获取告警列表
     * @param authentication 当前用户认证信息
     * @param alertDto 告警查询条件
     * @return 告警列表
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('HANDLER')")
    @GetMapping
    public Result<PageResult<AlertRecord>> getAlertList(Authentication authentication,
                                                        AlertDto alertDto) {
        Long operatorId = currentUserIdProvider.get(authentication);
        String operatorRole = currentUserRoleProvider.get(authentication);
        return Result.success(alertService.getAlertList(operatorId, operatorRole, alertDto));
    }

    /**
     * 处理告警，操作人及角色由 Token 确定。
     * @param id 告警ID
     * @param authentication 当前用户认证信息
     * @return 已处理的告警记录
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('HANDLER')")
    @PostMapping("/{id}/handle")
    public Result<AlertRecord> handleAlert(@PathVariable("id") Long id,
                                           Authentication authentication) {
        Long operatorId = currentUserIdProvider.get(authentication);
        String operatorRole = currentUserRoleProvider.get(authentication);
        return Result.success(alertService.handleAlert(id, operatorId, operatorRole));
    }
}
