package com.WorkOrder.user.dto.user;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月12日 21:34
 * @description 密码DTO，用于修改密码
 */
@Data
public class PasswordDto {
    @Size(min = 6,max = 32, message = "密码长度在6到32个字符之间")
    private String oldPassword;

    @Size(min = 6,max = 32, message = "密码长度在6到32个字符之间")
    private String newPassword;
}
