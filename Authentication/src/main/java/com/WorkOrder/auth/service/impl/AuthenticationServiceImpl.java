package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.auth.service.AuthenticationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月08日 23:45
 * @description
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    /**
     * 将 Spring Security 主体转换为 API 返回对象，避免把框架对象直接暴露给调用方。
     *
     * @param authentication 已认证的 OAuth2 主体
     * @return 当前用户资料
     */
    @Override
    public AuthenticatedUser toCurrentUser(Authentication authentication) {
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
