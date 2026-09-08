package com.WorkOrder.auth.controller;

import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.model.Result;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/** OAuth2 认证后的用户上下文接口。令牌签发由 /oauth/token 标准端点负责。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    /**
     * 从已校验的 OAuth2 JWT 中返回当前用户及角色。
     *
     * @param authentication Spring Security 注入的认证信息
     * @return 不含密码的当前用户资料
     */
    @GetMapping("/me")
    public Result<AuthenticatedUser> currentUser(Authentication authentication) {
        return Result.success(toCurrentUser(authentication));
    }

    /**
     * 将 Spring Security 主体转换为 API 返回对象，避免把框架对象直接暴露给调用方。
     *
     * @param authentication 已认证的 OAuth2 主体
     * @return 当前用户资料
     */
    private AuthenticatedUser toCurrentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String username = principal instanceof UserDetails
                ? ((UserDetails) principal).getUsername() : authentication.getName();
        LinkedHashSet<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AuthenticatedUser(null, username, null, roles);
    }
}

