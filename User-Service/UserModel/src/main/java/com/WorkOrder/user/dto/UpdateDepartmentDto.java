package com.WorkOrder.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 修改部门请求，所有字段均采用部分更新语义。
 */
@Getter
public class UpdateDepartmentDto {

    @Size(max = 100, message = "部门名称不能超过 100 个字符")
    private String name;

    private Long parentId;

    private Long managerId;

    @JsonIgnore
    private boolean namePresent;

    @JsonIgnore
    private boolean parentIdPresent;

    @JsonIgnore
    private boolean managerIdPresent;

    /**
     * 设置部门名称，并记录请求中显式提交了该字段。
     *
     * @param name 部门名称
     */
    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    /**
     * 设置上级部门，并记录请求中显式提交了该字段；传入 {@code null} 表示改为根部门。
     *
     * @param parentId 上级部门 ID
     */
    @JsonSetter("parentId")
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdPresent = true;
    }

    /**
     * 设置部门负责人，并记录请求中显式提交了该字段；传入 {@code null} 表示取消负责人。
     *
     * @param managerId 部门负责人用户 ID
     */
    @JsonSetter("managerId")
    public void setManagerId(Long managerId) {
        this.managerId = managerId;
        this.managerIdPresent = true;
    }
}
