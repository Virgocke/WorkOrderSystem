package com.WorkOrder.auth.controller;

import com.WorkOrder.auth.dto.ResetPasswordRequestDto;
import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.auth.dto.RegisterUserDto;
import com.WorkOrder.auth.service.AuthenticationService;
import com.WorkOrder.auth.service.RegisterService;
import com.WorkOrder.auth.service.SendCodeService;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.UserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;


/** OAuth2 认证后的用户上下文接口。令牌签发由 /oauth/token 标准端点负责。 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    private final RegisterService registerService;

    private final SendCodeService sendCodeService;

    /**
     * 创建认证用户上下文控制器。
     *
     * @param authenticationService 认证用户资料服务
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
     * 注册功能
     * @param user
     * @return 返回注册用户信息
     */
    @PostMapping("/register")
    public Result<UserResponse> RegisterUser(@Valid @RequestBody RegisterUserDto user){
        UserResponse register = registerService.register(user);
        return Result.success(register);
    }

    /**
     * 发送验证码
     * @param body
     * @return 邮箱
     */
    @PostMapping("/forget-password")
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
     * @param request
     * @return 是否重置成功
     */
    @PostMapping("/reset-password")
    public Result<Boolean> resetPassword(@RequestBody ResetPasswordRequestDto request){
        return Result.success(registerService.resetPassword(request));
    }
}
