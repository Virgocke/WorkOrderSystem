package com.WorkOrder.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月11日 02:26
 * @description 用户注册类，接受前端传入的注册数据
 */
@Data
public class RegisterUserDto {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度在3到20个字符之间")
    private String username;
    @Size(min = 6,max = 32, message = "密码长度在6到32个字符之间")
    private String password;
    private String realName;
    private String email;
    @Size(min = 11, max = 11, message = "手机号长度必须是11位")
    private String phone;
}
