package com.WorkOrder.user.service;

import com.WorkOrder.user.dto.UsersDto;
import org.springframework.security.core.Authentication;

/**
 * @author Virgor
 * @date 2026年09月12日 02:46
 * @description 返回已认证用户信息
 */
public interface AuthenticationService {
    UsersDto toCurrentUser(Authentication authentication);
}
