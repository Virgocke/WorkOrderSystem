package com.WorkOrder.auth.service;

import com.WorkOrder.auth.model.AuthenticatedUser;
import org.springframework.security.core.Authentication;

/**
 * @author Virgor
 * @date 2026年09月08日 23:43
 * @description
 */
public interface AuthenticationService {
    /**
     * 根据已校验的 OAuth2 主体加载当前用户资料和权限。
     *
     * @param authentication Spring Security 认证主体
     * @return 前端登录态所需的当前用户资料
     */
    AuthenticatedUser toCurrentUser(Authentication authentication);
}
