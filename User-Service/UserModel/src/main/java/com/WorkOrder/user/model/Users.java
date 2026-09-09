package com.WorkOrder.user.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月08日 17:38
 * @description 用户属性表
 */
@SuppressWarnings("serial")
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("users")
public class Users {
    private Long id;
    private String username;
    private String password;
    private String realname;
    private String email;
    private String phone;
    private Long departmentId; // 部门ID
    private int role; // 角色 0:普通用户 1:处理人 2:管理员
    private int status; // 状态
    private String createTime; // 创建时间
}
