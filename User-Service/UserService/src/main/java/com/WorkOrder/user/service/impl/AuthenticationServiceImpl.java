package com.WorkOrder.user.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.user.dto.user.UsersDto;
import com.WorkOrder.user.mapper.UsersMapper;
import com.WorkOrder.user.service.AuthenticationService;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author Virgor
 * @date 2026年09月12日 02:47
 * @description 认证服务实现
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private UsersMapper usersMapper;

    public AuthenticationServiceImpl(UsersMapper usersMapper){
        this.usersMapper = usersMapper;
    }

    /**
     * 获取当前用户
     *
     * @param authentication 认证信息
     * @return 当前用户
     */
    @Override
    public UsersDto toCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException(SystemExceptionEnum.ACCOUNT_OFFLINE.getErrMessage());
        }

        UsersDto user = usersMapper.findProfileByUsername(authentication.getName());
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
