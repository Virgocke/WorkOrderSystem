package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.handler.service.HandlerUserService;
import com.WorkOrder.security.CurrentUserIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 02:29
 * @description 处理人控制器
 */
@RestController
@RequestMapping("/handlers")
@RequiredArgsConstructor
public class HandlerController {

    private final HandlerUserService handlerService;
    private final CurrentUserIdProvider currentUserIdProvider;

    @GetMapping("/options")
    public Result<List<HandlerProfile>> getHandler(Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        String username = authentication.getName();
        return Result.success(handlerService.getHandler(userId, username));
    }
}
