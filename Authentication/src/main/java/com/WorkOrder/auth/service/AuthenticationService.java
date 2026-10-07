package com.WorkOrder.auth.service;

import com.WorkOrder.auth.model.AuthenticatedUser;
import org.springframework.security.core.Authentication;

/**
 * @author Virgor
 * @date 2026年09月08日 23:43
 * @description 根据已认证的登录信息读取当前用户资料与权限。
 */
public interface AuthenticationService {
    /**
     * 根据认证主体读取当前用户资料，并校验账号状态、补充权限。
     *
     * @param authentication Spring Security 认证主体
     * @return 当前启用用户的资料和权限
     */
    AuthenticatedUser toCurrentUser(Authentication authentication);
}
