package com.WorkOrder.ticket.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月13日 03:47
 * @description 工单分类DTO
 */

@Data
public class TicketCategoryDto {
    @NotBlank(message = "名称不能为空")
    private String name;

    private Long parentId;

    @Size(min = 1, max = 4, message = "默认优先级必须在1到4之间")
    @NotBlank(message = "默认优先级不能为空")
    private int defaultPriority;

    @NotBlank(message = "默认响应SLA不能为空")
    private int defaultResponseSla;

    @NotBlank(message = "默认解决SLA不能为空")
    private int defaultResolutionSla;

    private String description;
}