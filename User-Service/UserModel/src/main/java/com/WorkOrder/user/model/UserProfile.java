package com.WorkOrder.user.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/** 对外暴露的用户资料，绝不包含密码字段。 */
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
    private Set<UserRole> roles = new LinkedHashSet<>();
    private Boolean enabled;
    private HandlerProfile handlerProfile;
}
