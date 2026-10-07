package com.WorkOrder.handler.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员直接配置处理人技能时提交的单个技能项。
 */
@Data
public class HandlerSkillRequest {

    @NotNull(message = "技能 ID 不能为空")
    @Min(value = 1, message = "技能 ID 必须为正整数")
    private Long skillId;

    @NotNull(message = "熟练度不能为空")
    @Min(value = 1, message = "熟练度必须在 1 到 5 之间")
    @Max(value = 5, message = "熟练度必须在 1 到 5 之间")
    private Integer proficiency;
}
