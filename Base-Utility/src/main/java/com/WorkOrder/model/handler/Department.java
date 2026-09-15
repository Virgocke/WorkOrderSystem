package com.WorkOrder.model.handler;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月15日 04:26
 * @description 部门信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Department {
    @ApiModelProperty(value = "部门ID")
    private Long id;
    @ApiModelProperty(value = "部门名称")
    private String name;
    @ApiModelProperty(value = "上级部门ID")
    private Long parentId;
    @ApiModelProperty(value = "部门负责人用户ID")
    private Long managerId;
    @ApiModelProperty(value = "创建时间")
    private String createdAt;
    @ApiModelProperty(value = "子部门列表")
    private Department[] children;
}
