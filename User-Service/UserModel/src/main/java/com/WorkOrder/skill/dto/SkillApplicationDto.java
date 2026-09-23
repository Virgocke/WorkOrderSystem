package com.WorkOrder.skill.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.*;

/**
 * @author Virgor
 * @date 2026年09月23日 17:30
 * @description 技能申请数据传输对象
 */
@Data
public class SkillApplicationDto {

    @Positive
    @NotBlank
    private Long skillId;

    @Min(0)
    @Max(5)
    @ApiModelProperty(value = "REMOVE 可不传 proficiency")
    private int proficiency = 0;

    @Pattern(regexp = "ADD|ADJUST|DELETE", message = "只能是ADD、ADJUST或DELETE")
    @NotBlank
    private String type;

    @NotBlank
    private String reason;
}
