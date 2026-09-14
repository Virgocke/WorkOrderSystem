package com.WorkOrder.model.user;

import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月11日 02:20
 * @description 用户响应类，用于封装用户信息返回前端
 */
@Data
public class UserResponse {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Long departmentId; // 部门ID
    private int role; // 角色 0:普通用户 1:处理人 2:管理员
    private int status; // 状态
    private String createdAt; // 创建时间

}
