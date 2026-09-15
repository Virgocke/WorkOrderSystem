package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserResponse;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.user.dto.PasswordDto;
import com.WorkOrder.user.dto.UserUpdateDto;
import com.WorkOrder.user.dto.UsersDto;
import com.WorkOrder.user.model.CreateUserRequest;
import com.WorkOrder.user.model.UpdateHandlerProfileRequest;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.user.service.AuthenticationService;
import com.WorkOrder.user.service.UserDirectoryService;
import com.WorkOrder.user.service.UserInfoUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 用户、处理人及其派单基础资料接口。 */
@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserDirectoryService userDirectoryService;
    private final AuthenticationService authenticationService;
    private final UserInfoUpdateService userInfoUpdateService;
    private final CurrentUserIdProvider currentUserIdProvider;



    /**
     * 获取当前登录用户资料。
     * @param authentication
     * @return 当前登录用户资料
     */
    @GetMapping("/me")
    public Result<UsersDto> currentUser(Authentication authentication) {
        return Result.success(authenticationService.toCurrentUser(authentication));
    }

    /**
     * 更新当前登录用户资料。
     * @param userUpdateDto
     * @return 更新后的用户资料
     */
    @PutMapping("/me")
    public Result<UserResponse> updateUserInfo(
            Authentication authentication,
            @Valid @RequestBody UserUpdateDto userUpdateDto) {

        Long userId = currentUserIdProvider.get(authentication);

        return Result.success(
                userInfoUpdateService.updateUserInfo(userId, userUpdateDto)
        );
    }

    @PutMapping("/me/password")
    public Result<Boolean> updatePassword(Authentication authentication,
                                          @Valid @RequestBody PasswordDto passwordDto){
        return Result.success(userInfoUpdateService.updateUserPassword(authentication.getName(), passwordDto));
    }









    //======================================调试用=======================================
    /**
     * 创建用户或处理人基础资料。
     *
     * @param request 创建请求
     * @return 创建后的用户资料
     */
//    @PostMapping
//    public Result<UserProfile> create(@Valid @RequestBody CreateUserRequest request) {
//        return Result.success(userDirectoryService.create(request));
//    }

    /**
     * 查询指定用户的公开资料。
     *
     * @param id 用户主键
     * @return 用户资料
     */
    @GetMapping("/{id}")
    public Result<UserProfile> getById(@PathVariable Long id) {
        return Result.success(userDirectoryService.getById(id));
    }

    /**
     * 获取可参与自动派单的处理人列表。
     *
     * @return 处理人资料列表
     */
//    @GetMapping("/handlers")
//    public Result<List<UserProfile>> listHandlers() {
//        return Result.success(userDirectoryService.listHandlers());
//    }

    /**
     * 更新处理人的容量和技能标签。
     *
     * @param id 用户主键
     * @param request 档案更新请求
     * @return 更新后的用户资料
     */
//    @PutMapping("/{id}/handler-profile")
//    public Result<UserProfile> updateHandlerProfile(@PathVariable Long id,
//                                                     @Valid @RequestBody UpdateHandlerProfileRequest request) {
//        return Result.success(userDirectoryService.updateHandlerProfile(id, request));
//    }
}
