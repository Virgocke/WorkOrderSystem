package com.WorkOrder.auth.service;

import com.WorkOrder.auth.dto.RegisterUserDto;
import com.WorkOrder.auth.dto.ResetPasswordRequestDto;
import com.WorkOrder.model.UserResponse;

/**
 * @author Virgor
 * @date 2026年09月11日 02:15
 * @description 用户注册
 */
public interface RegisterService {
    UserResponse register(RegisterUserDto registerUserDto);

    Boolean resetPassword(ResetPasswordRequestDto request);
}
