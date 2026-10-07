package com.WorkOrder.security;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 从当前认证信息读取登录角色，供操作日志保存角色快照。
 */
@Component
public class CurrentUserRoleProvider {

    /**
     * 从当前认证权限读取登录角色快照，供操作日志记录。
     *
     * @param authentication 携带 Spring Security 角色权限的当前认证信息
     * @return 认证角色快照 ADMIN、HANDLER 或 USER；多个角色按 ADMIN、HANDLER、USER 的顺序优先返回
     */
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
