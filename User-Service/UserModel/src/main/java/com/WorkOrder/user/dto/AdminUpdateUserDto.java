package com.WorkOrder.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员编辑用户请求。
 *
 * <p>字段采用部分更新语义；presence 标记用于区分“未提交字段”和
 * “显式提交 null”，从而允许清空可空的联系方式或部门。</p>
 */
@Getter
@JsonIgnoreProperties({
        "realNamePresent", "passwordPresent", "emailPresent", "phonePresent",
        "departmentIdPresent", "rolePresent", "statusPresent"
})
public class AdminUpdateUserDto {

    @Size(max = 50, message = "姓名不能超过50个字符")
    @Pattern(regexp = ".*\\S.*", message = "姓名不能为空")
    private String realName;

    @Pattern(regexp = "^$|^(?=.*\\S).{6,20}$", message = "密码长度在6到20个字符之间")
    private String password;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Positive(message = "部门ID必须为正整数")
    private Long departmentId;

    @Min(value = 0, message = "角色取值必须为0到2")
    @Max(value = 2, message = "角色取值必须为0到2")
    private Integer role;

    @Min(value = 0, message = "状态取值必须为0或1")
    @Max(value = 1, message = "状态取值必须为0或1")
    private Integer status;

    private boolean realNamePresent;
    private boolean passwordPresent;
    private boolean emailPresent;
    private boolean phonePresent;
    private boolean departmentIdPresent;
    private boolean rolePresent;
    private boolean statusPresent;

    /**
     * 设置真实姓名，并记录请求中明确包含该字段。
     *
     * @param realName 待更新的真实姓名，可以为 null
     */
    public void setRealName(String realName) {
        this.realNamePresent = true;
        this.realName = realName;
    }

    /**
     * 设置密码，并记录请求中明确包含该字段。
     *
     * @param password 待更新的密码，可以为 null
     */
    public void setPassword(String password) {
        this.passwordPresent = true;
        this.password = password;
    }

    /**
     * 设置邮箱，并记录请求中明确包含该字段。
     *
     * @param email 待更新的邮箱，可以为 null
     */
    public void setEmail(String email) {
        this.emailPresent = true;
        this.email = email;
    }

    /**
     * 设置手机号，并记录请求中明确包含该字段。
     *
     * @param phone 待更新的手机号，可以为 null
     */
    public void setPhone(String phone) {
        this.phonePresent = true;
        this.phone = phone;
    }

    /**
     * 设置部门 ID，并记录请求中明确包含该字段。
     *
     * @param departmentId 待更新的部门 ID，可以为 null
     */
    public void setDepartmentId(Long departmentId) {
        this.departmentIdPresent = true;
        this.departmentId = departmentId;
    }

    /**
     * 设置用户角色，并记录请求中明确包含该字段。
     *
     * @param role 待更新的用户角色，可以为 null
     */
    public void setRole(Integer role) {
        this.rolePresent = true;
        this.role = role;
    }

    /**
     * 设置账号状态，并记录请求中明确包含该字段。
     *
     * @param status 待更新的账号状态，可以为 null
     */
    public void setStatus(Integer status) {
        this.statusPresent = true;
        this.status = status;
    }
}
