package com.WorkOrder.auth.service;

import com.WorkOrder.auth.dto.RegisterUserDto;
import com.WorkOrder.auth.dto.AuthenticatedUserDto;
import com.WorkOrder.auth.dto.ResetPasswordRequestDto;
import com.WorkOrder.model.user.UserResponse;

/**
 * @author Virgor
 * @date 2026年09月11日 02:15
 * @description 用户注册
 */
public interface RegisterService {
    UserResponse register(RegisterUserDto registerUserDto);

    /** 注册账号并返回可直接使用的登录态。 */
    AuthenticatedUserDto registerAndLogin(RegisterUserDto registerUserDto);

    Boolean resetPassword(ResetPasswordRequestDto request);
}
