package com.WorkOrder.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/** 已完成身份校验的用户上下文。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {
    private Long userId;
    private String username;
    private String realName;
    private Set<String> roles = new LinkedHashSet<>();
}
