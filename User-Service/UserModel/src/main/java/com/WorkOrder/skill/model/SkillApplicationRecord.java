package com.WorkOrder.skill.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 技能调整申请持久化实体，对应 skill_applications 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("skill_applications")
public class SkillApplicationRecord {

    /**
     * 申请主键，由数据库自增生成。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 申请人用户 ID，取自登录 Token。
     */
    private Long handlerId;

    /**
     * 目标技能标签 ID，对应 skill_tags.id。
     */
    private Long skillTagId;

    /**
     * 目标熟练度（1-5）；REMOVE 类型时为 null。
     */
    private Integer proficiency;

    /**
     * 调整类型：ADD、ADJUST、REMOVE。
     */
    private String type;

    /**
     * 申请理由。
     */
    private String reason;

    /**
     * 审核状态：PENDING、APPROVED、REJECTED。
     */
    private String status;

    /**
     * 审核人用户 ID，对应 users.id；待审核时为 null。
     */
    private Long reviewerId;

    /**
     * 审核理由；未审核或未填写时为 null。
     */
    private String reviewReason;

    /**
     * 审核时间；待审核时为 null。
     */
    private LocalDateTime reviewedAt;

    /**
     * 申请时间，由数据库生成。
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间，由数据库维护。
     */
    private LocalDateTime updatedAt;
}
