package com.WorkOrder.user.dto.user;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月12日 02:44
 * @description 用户更新DTO
 */

@Data
public class UserUpdateDto {

    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}