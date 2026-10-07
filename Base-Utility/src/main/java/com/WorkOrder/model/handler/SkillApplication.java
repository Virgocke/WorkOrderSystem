package com.WorkOrder.model.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 处理人技能调整申请响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillApplication {

    /**
     * 申请 ID。
     */
    private Long id;

    /**
     * 申请人用户 ID。
     */
    private Long handlerId;

    /**
     * 申请人姓名。
     */
    private String handlerName;

    /**
     * 目标技能标签 ID。
     */
    private Long skillId;

    /**
     * 目标技能名称。
     */
    private String skillName;

    /**
     * 目标熟练度，取值范围 1-5；REMOVE 类型时为 null。
     */
    private Integer proficiency;

    /**
     * 调整类型：ADD、ADJUST、REMOVE。
     */
    private String type;

    /**
     * 原申请理由，可为 null，审核不覆盖。
     */
    private String reason;

    /**
     * 审核状态：PENDING、APPROVED、REJECTED。
     */
    private String status;

    /**
     * 申请时间，格式为 yyyy-MM-dd HH:mm:ss。
     */
    private String createdAt;
    /**
     * 审核意见、审核人及审核时间；待审核时为空。
     */
    private String reviewComment;
    /**
     * 完成本次审核的管理员用户 ID。
     */
    private Long reviewerId;
    /**
     * 审核完成时间；待审核时为空。
     */
    private String reviewedAt;
}
