package com.WorkOrder.skill.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 新增技能标签请求。 */
@Data
public class CreateSkillTagRequest {

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 50, message = "技能名称不能超过 50 个字符")
    private String name;

    @Size(max = 255, message = "技能描述不能超过 255 个字符")
    private String description;
}
