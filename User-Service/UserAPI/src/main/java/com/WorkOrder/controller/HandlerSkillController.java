package com.WorkOrder.controller;

import com.WorkOrder.handler.service.HandlerUserService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.security.CurrentUserIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 处理人查看自身技能的接口。
 */
@RestController
@RequestMapping("/handler-skills")
@RequiredArgsConstructor
public class HandlerSkillController {

    private final HandlerUserService handlerService;
    private final CurrentUserIdProvider currentUserIdProvider;

    /**
     * 根据已验证 Token 中的用户 ID 查询当前处理人档案。
     *
     * @param authentication 当前认证信息
     * @return 当前处理人档案及技能
     */
    @PreAuthorize("hasRole('HANDLER')")
    @GetMapping("/me")
    public Result<HandlerProfile> getMySkills(Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(handlerService.getMyHandlerProfile(userId));
    }
}
