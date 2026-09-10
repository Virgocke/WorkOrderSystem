package com.WorkOrder.auth.service;

import com.WorkOrder.auth.model.RegisterUser;
import com.WorkOrder.model.UserResponse;

/**
 * @author Virgor
 * @date 2026年09月11日 02:15
 * @description 用户注册
 */
public interface RegisterService {
    UserResponse register(RegisterUser registerUser);
}
