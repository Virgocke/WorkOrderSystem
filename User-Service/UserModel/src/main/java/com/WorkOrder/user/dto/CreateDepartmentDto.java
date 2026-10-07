package com.WorkOrder.user.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 新增部门请求。
 */
@Data
public class CreateDepartmentDto {

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100, message = "部门名称不能超过 100 个字符")
    private String name;

    private Long parentId;

    private Long managerId;
}
