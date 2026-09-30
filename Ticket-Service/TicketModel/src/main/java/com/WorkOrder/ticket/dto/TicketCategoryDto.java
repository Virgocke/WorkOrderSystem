package com.WorkOrder.ticket.dto;


import lombok.Data;

import javax.validation.constraints.*;
import java.util.List;

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

    @Min(1)
    @Max(4)
    @NotNull(message = "默认优先级不能为空")
    private int defaultPriority;

    @NotNull(message = "默认响应SLA不能为空")
    private int defaultResponseSla;

    @NotNull(message = "默认解决SLA不能为空")
    private int defaultResolutionSla;

    private String description;

    /** 当前分类要求的技能标签 ID；省略时保留原配置，空数组表示清空。 */
    private List<Long> requiredSkillIds;
}
