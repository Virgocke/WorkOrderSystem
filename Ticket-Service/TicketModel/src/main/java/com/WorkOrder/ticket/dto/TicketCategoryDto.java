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

    /** 响应时限（分钟）；null 或省略表示使用系统默认值。 */
    @Min(5)
    @Max(2880)
    private Integer defaultResponseSla;

    /** 解决时限（分钟）；null 或省略表示使用系统默认值。 */
    @Min(30)
    @Max(10080)
    private Integer defaultResolutionSla;

    private String description;

    /** 当前分类要求的技能标签 ID；省略时保留原配置，空数组表示清空。 */
    private List<Long> requiredSkillIds;
}
