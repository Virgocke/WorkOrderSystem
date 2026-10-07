package com.WorkOrder.auth.dto;

import com.WorkOrder.auth.model.AuthenticatedUser;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 注册或登录成功后返回给前端的完整登录态。
 */
@Data
@AllArgsConstructor
public class AuthenticatedUserDto {
    private String token;
    private AuthenticatedUser user;
    private List<String> permissions;
}
