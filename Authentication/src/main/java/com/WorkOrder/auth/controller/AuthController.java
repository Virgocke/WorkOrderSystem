package com.WorkOrder.auth.controller;

import com.WorkOrder.auth.model.AuthenticatedUser;
import com.WorkOrder.auth.model.RegisterUser;
import com.WorkOrder.auth.service.AuthenticationService;
import com.WorkOrder.auth.service.RegisterService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.UserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** OAuth2 认证后的用户上下文接口。令牌签发由 /oauth/token 标准端点负责。 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    private final RegisterService registerService;

    /**
     * 创建认证用户上下文控制器。
     *
     * @param authenticationService 认证用户资料服务
     */
    public AuthController(AuthenticationService authenticationService, RegisterService registerService) {
        this.authenticationService = authenticationService;
        this.registerService = registerService;
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

    @PostMapping("/register")
    public Result<UserResponse> RegisterUser(RegisterUser user){
        registerService.register(user);
        return Result.success();
    }
}
