package com.WorkOrder.user.service;

import com.WorkOrder.model.user.UserResponse;
import com.WorkOrder.user.dto.PasswordDto;
import com.WorkOrder.user.dto.UserUpdateDto;

/**
 * @author Virgor
 * @date 2026年09月12日 21:01
 * @description 用户信息更新服务
 */
public interface UserInfoUpdateService {

    /**
     * 更新用户的真实姓名、邮箱和手机号。
     *
     * @param userId 用户 ID
     * @param userUpdateDto 待更新的用户资料
     * @return 更新后的用户资料
     */
    UserResponse updateUserInfo(Long userId, UserUpdateDto userUpdateDto);

    /**
     * 校验旧密码并更新账号密码。
     *
     * @param username 账号
     * @param passwordDto 旧密码和新密码
     * @return 密码修改成功时为 true，失败时抛出异常
     */
    Boolean updateUserPassword(String username, PasswordDto passwordDto);
}
