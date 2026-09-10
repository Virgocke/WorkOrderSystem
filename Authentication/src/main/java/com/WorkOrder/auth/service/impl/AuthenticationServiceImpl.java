package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.mapper.UsersMapper;
import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.auth.service.AuthenticationService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;

/** 根据 OAuth2 认证主体查询并组装前端登录态所需的用户资料与权限。 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final UsersMapper usersMapper;

    /**
     * 创建认证用户资料服务。
     *
     * @param usersMapper 登录与权限数据访问接口
     */
    public AuthenticationServiceImpl(UsersMapper usersMapper) {
        this.usersMapper = usersMapper;
    }


    /**
     * 将 Spring Security 主体转换为 API 返回对象，避免把框架对象直接暴露给调用方。
     *
     * @param authentication 已认证的 OAuth2 主体
     * @return 当前用户资料
     * @throws InsufficientAuthenticationException 请求中不存在有效认证主体时抛出
     * @throws SystemException 用户已不存在或已被禁用时抛出
     */
    @Override
    public AuthenticatedUser toCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException(SystemExceptionEnum.ACCOUNT_OFFLINE.getErrMessage());
        }

        AuthenticatedUser user = usersMapper.findProfileByUsername(authentication.getName());
        if (user == null) {
            throw new SystemException(SystemExceptionEnum.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

        java.util.List<String> permissions = usersMapper.findPermissionCodes(user.getId(), user.getRole());
        user.setPermissions(new LinkedHashSet<>(permissions == null ? Collections.emptyList() : permissions));
        return user;
    }
}
