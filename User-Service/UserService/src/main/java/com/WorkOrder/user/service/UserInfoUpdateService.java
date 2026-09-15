package com.WorkOrder.user.service;

import com.WorkOrder.model.user.UserResponse;
import com.WorkOrder.user.dto.user.PasswordDto;
import com.WorkOrder.user.dto.user.UserUpdateDto;

/**
 * @author Virgor
 * @date 2026年09月12日 21:01
 * @description 用户信息更新服务
 */
public interface UserInfoUpdateService {

    UserResponse updateUserInfo(Long userId, UserUpdateDto userUpdateDto);

    Boolean updateUserPassword(String username, PasswordDto passwordDto);
}
