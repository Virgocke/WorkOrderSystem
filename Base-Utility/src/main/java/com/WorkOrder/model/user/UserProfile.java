package com.WorkOrder.model.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import java.util.List;


/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 对外暴露的用户资料，绝不包含密码字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Long departmentId;
    private String departmentName;
    private int role;
    private int status;
    private LocalDateTime createdAt;
    private List<String> permissions;
}
