package com.WorkOrder.user.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月21日 23:17
 * @description 管理员创建用户DTO类
 */
@Data
public class CreateUserDto {
    @NotBlank
    private String username;
    @NotBlank
    private String realName;
    @NotBlank
    @Size(min = 6, max = 20, message = "密码长度在6到20个字符之间")
    private String password;
    @Email
    private String email;
    @NotBlank
    @Size(min = 11, max = 11, message = "手机号长度必须为11位")
    private String phone;
    private Long departmentId;
    @NotNull
    private int role;
    @NotNull
    private int status;
}
