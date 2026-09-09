package com.WorkOrder.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 已完成身份校验的用户上下文。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {
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
    private Set<String> permissions = new LinkedHashSet<>();
}
