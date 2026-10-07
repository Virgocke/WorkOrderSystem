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
    /**
     * 注册启用的普通用户账号。
     *
     * @param registerUserDto 待注册账号、密码和用户资料
     * @return 注册成功后的用户资料
     */
    UserResponse register(RegisterUserDto registerUserDto);

    /**
     * 注册账号并返回可直接使用的登录态。
     *
     * @param registerUserDto register用户请求数据
     * @return Authenticated用户请求数据
     */
    AuthenticatedUserDto registerAndLogin(RegisterUserDto registerUserDto);

    /**
     * 验证邮箱验证码并重置账号密码。
     *
     * @param request 邮箱、验证码和新密码
     * @return 密码重置成功时为 true，失败时抛出异常
     */
    Boolean resetPassword(ResetPasswordRequestDto request);
}
