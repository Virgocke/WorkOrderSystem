package com.WorkOrder.security;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 从当前认证信息读取登录角色，供操作日志保存角色快照。
 */
@Component
public class CurrentUserRoleProvider {

    public String get(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }

        // 存在多个角色时，优先记录管理员，其次处理人、普通用户。
        for (String role : new String[]{"ADMIN", "HANDLER", "USER"}) {
            boolean hasRole = authentication.getAuthorities().stream()
                    .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
            if (hasRole) {
                return role;
            }
        }

        throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
    }
}
