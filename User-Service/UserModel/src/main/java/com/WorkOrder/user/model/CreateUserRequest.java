package com.WorkOrder.user.model;

import com.WorkOrder.user.enums.UserRole;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 创建用户请求
 */
@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过 50 个字符")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
    private Long departmentId;
    private Set<UserRole> roles = new LinkedHashSet<>();
}
