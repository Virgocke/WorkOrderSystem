package com.WorkOrder.skill.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员审核技能调整申请的请求。
 */
@Data
public class ReviewSkillApplicationDto {

    /**
     * 审核结果，只允许通过或驳回。
     */
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED", message = "审核状态只能是APPROVED或REJECTED")
    private String status;

    /**
     * 可选审核理由。
     */
    @Size(max = 300)
    private String reason;
}
