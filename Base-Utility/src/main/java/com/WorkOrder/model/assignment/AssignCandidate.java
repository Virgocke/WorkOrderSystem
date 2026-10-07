package com.WorkOrder.model.assignment;

import com.WorkOrder.model.handler.HandlerProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 智能分配候选人响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignCandidate {

    /**
     * 候选处理人档案。
     */
    private HandlerProfile handler;

    /**
     * 综合得分，取值范围 0-100。
     */
    private BigDecimal totalScore;

    /**
     * 技能匹配得分，取值范围 0-100。
     */
    private BigDecimal skillMatchScore;

    /**
     * 负载得分，取值范围 0-100。
     */
    private BigDecimal loadScore;

    /**
     * SLA 表现得分，取值范围 0-100。
     */
    private BigDecimal slaScore;

    /**
     * 历史评分折算得分，取值范围 0-100。
     */
    private BigDecimal ratingScore;

    /**
     * 本次推荐实际采用的权重及配置版本。
     */
    private AssignmentWeightsSnapshot weights;

    /**
     * 推荐原因说明。
     */
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}
