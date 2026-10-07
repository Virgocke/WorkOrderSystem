package com.WorkOrder.model.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月15日 04:28
 * @description 处理人档案响应对象，供处理人列表、详情及分配候选人接口使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandlerProfile {

    /**
     * 用户 ID。为兼容前端通用列表，同时返回 id 和 userId。
     */
    private Long id;

    /**
     * 用户 ID，不是 handler_profiles 表的自增主键。
     */
    private Long userId;

    /**
     * 处理人姓名。
     */
    private String realName;

    /**
     * 登录账号。
     */
    private String username;

    /**
     * 所属部门 ID，未分配部门时为 null。
     */
    private Long departmentId;

    /**
     * 所属部门名称，未分配部门时为 null。
     */
    private String departmentName;

    /**
     * 最大同时处理工单数。
     */
    private int maxCapacity;

    /**
     * 当前在办工单数。
     */
    private int currentLoad;

    /**
     * 平均响应时长，单位：分钟。
     */
    private int avgResponseMinutes;

    /**
     * 平均解决时长，单位：分钟。
     */
    private int avgResolutionMinutes;

    /**
     * SLA 达成率，取值范围 0-100。
     */
    private BigDecimal slaComplianceRate;

    /**
     * 平均用户评分，取值范围 1-5。
     */
    private BigDecimal ratingScore;

    /**
     * 处理人技能列表。
     */
    @Builder.Default
    private List<HandlerSkillItem> skills = new ArrayList<>();

    /**
     * 状态：0-停用，1-启用。
     */
    private int status;
}
