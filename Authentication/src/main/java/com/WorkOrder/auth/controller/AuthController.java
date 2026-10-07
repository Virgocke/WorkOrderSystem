package com.WorkOrder.auth.controller;

import com.WorkOrder.auth.dto.ResetPasswordRequestDto;
import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.auth.dto.AuthenticatedUserDto;
import com.WorkOrder.auth.dto.RegisterUserDto;
import com.WorkOrder.auth.service.AuthenticationService;
import com.WorkOrder.auth.service.RegisterService;
import com.WorkOrder.auth.service.SendCodeService;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;


/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 用户认证、注册及 OAuth2 登录态接口。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    private final RegisterService registerService;

    private final SendCodeService sendCodeService;

    /**
     * 构建用户认证、注册及 OAuth2 登录态接口。
     *
     * @param authenticationService 认证用户资料服务
     * @param registerService Register服务
     * @param sendCodeService Send代码服务
     */
    public AuthController(AuthenticationService authenticationService,
                          RegisterService registerService,
                          SendCodeService sendCodeService) {
        this.authenticationService = authenticationService;
        this.registerService = registerService;
        this.sendCodeService = sendCodeService;
    }

    /**
     * 从已校验的 OAuth2 JWT 中返回当前用户及角色。
     *
     * @param authentication Spring Security 注入的认证信息
     * @return 不含密码的当前用户资料
     */
    @GetMapping("/me")
    public Result<AuthenticatedUser> currentUser(Authentication authentication) {
        return Result.success(authenticationService.toCurrentUser(authentication));
    }

    /**
     * 注册并直接建立登录态，避免浏览器在注册后再额外请求 OAuth2 令牌端点。
     *
     * @param user 用户
     * @return 返回注册用户信息
     */
    @PostMapping("/register")
    public Result<AuthenticatedUserDto> RegisterUser(@Valid @RequestBody RegisterUserDto user){
        return Result.success(registerService.registerAndLogin(user));
    }

    /**
     * 发送验证码
     *
     * @param body 包含 email 字段的验证码发送请求
     * @return 发送成功后返回邮箱地址的统一响应
     */
    @PostMapping("/forgot-password")
    public Result<Map<String,String>> sendCode(@RequestBody Map<String,String> body){
        String email = body == null ? null : body.get("email");
        if(!StringUtils.hasText(email)) {
            throw new SystemException("请输入邮箱");
        }
        email = email.trim();
        sendCodeService.sendCode(email);
        return Result.success(Collections.singletonMap("email", email));
    }

    /**
     * 重置密码
     *
     * @param request Reset密码请求请求数据
     * @return 是否重置成功
     */
    @PostMapping("/reset-password")
    public Result<Boolean> resetPassword(@RequestBody ResetPasswordRequestDto request){
        return Result.success(registerService.resetPassword(request));
    }

}
