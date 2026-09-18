package com.WorkOrder.assignment.controller;

import com.WorkOrder.assignment.dto.SaveConfigurationsDto;
import com.WorkOrder.assignment.model.ConfigurationItem;
import com.WorkOrder.assignment.service.ConfigurationService;
import com.WorkOrder.model.Result;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.security.CurrentUserRoleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 沿用系统配置接口契约，操作人由后端认证信息取得。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/configurations")
public class ConfigurationController {
    private final ConfigurationService configurationService;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;

    /** 获取配置列表及保存时所需的版本号。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ConfigurationItem>> list(Authentication authentication) {
        return Result.success(configurationService.list(currentUserRoleProvider.get(authentication)));
    }

    /** 在一个事务中保存配置并记录修改前后的值。 */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> save(@Valid @RequestBody SaveConfigurationsDto dto,
                                Authentication authentication) {
        return Result.success(configurationService.save(dto, currentUserIdProvider.get(authentication),
                currentUserRoleProvider.get(authentication)));
    }
}
