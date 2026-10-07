package com.WorkOrder.model.ticket;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月15日 03:14
 * @description 返回前端的工单类别
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketCategory {
    @ApiModelProperty(value = "ID")
    private Long id;
    @ApiModelProperty(value = "类别名称")
    private String name;
    @ApiModelProperty(value = "父类别ID")
    private Long parentId;
    @ApiModelProperty(value = "默认优先级")
    private int defaultPriority;
    @ApiModelProperty(value = "默认响应SLA")
    /**
     * 响应时限（分钟）；null 表示使用系统默认值。
     */
    private Integer defaultResponseSla;
    @ApiModelProperty(value = "默认解决SLA")
    /**
     * 解决时限（分钟）；null 表示使用系统默认值。
     */
    private Integer defaultResolutionSla;
    @ApiModelProperty(value = "描述")
    private String description;
    @ApiModelProperty(value = "子类别")
    private TicketCategory[] children;
}