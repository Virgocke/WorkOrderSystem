package com.WorkOrder.assignment.service.impl;

import com.WorkOrder.assignment.config.AssignWeightsProperties;
import com.WorkOrder.assignment.mapper.AssignEngineMapper;
import com.WorkOrder.assignment.service.AssignEngineService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.model.handler.HandlerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description 推荐候选人服务实现，按技能、负载、SLA和历史评分计算综合分。
 */
@Service
@RequiredArgsConstructor
public class AssignEngineServiceImpl implements AssignEngineService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWENTY = new BigDecimal("20");

    private final AssignEngineMapper assignEngineMapper;
    private final AssignWeightsProperties weights;

    @Override
    public List<AssignCandidate> recommend(Long ticketId, String operatorRole) {
        // 当前角色来自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (ticketId == null || ticketId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (assignEngineMapper.selectTicketId(ticketId) == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        List<HandlerProfile> handlers = assignEngineMapper.selectEnabledHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyList();
        }
        return handlers.stream()
                .map(this::buildCandidate)
                .sorted(Comparator.comparing(AssignCandidate::getTotalScore).reversed()
                        .thenComparing(candidate -> candidate.getHandler().getUserId()))
                .collect(Collectors.toList());
    }

    /**
     * 构建候选人得分与推荐原因，分项及综合分均保留一位小数。
     */
    private AssignCandidate buildCandidate(HandlerProfile handler) {
        //todo 接入工单分类与所需技能的关联规则，结合处理人技能及熟练度计算技能匹配分，暂计0分。
        BigDecimal skillMatchScore = BigDecimal.ZERO;
        //todo 接入 Redis 实时负载及用户服务的负载维护，目前使用 handler_profiles.current_load。
        BigDecimal loadScore = calculateLoadScore(handler);
        //todo 接入 SLA-Monitor 绩效聚合，目前使用处理人档案中已有的 SLA 达成率。
        BigDecimal slaScore = normalizeScore(handler.getSlaComplianceRate());
        //todo 接入评价模块的历史评分聚合，目前使用处理人档案中已有的平均评分。
        BigDecimal ratingScore = normalizeScore(handler.getRatingScore() == null
                ? BigDecimal.ZERO : handler.getRatingScore().multiply(TWENTY));

        //todo 接入系统配置模块的 assignWeights 动态配置，目前通过 assignment.weights 配置权重。
        BigDecimal totalWeight = weights.getSkill().add(weights.getLoad())
                .add(weights.getSla()).add(weights.getRating());
        BigDecimal totalScore = skillMatchScore.multiply(weights.getSkill())
                .add(loadScore.multiply(weights.getLoad()))
                .add(slaScore.multiply(weights.getSla()))
                .add(ratingScore.multiply(weights.getRating()))
                .divide(totalWeight, 1, RoundingMode.HALF_UP);

        return AssignCandidate.builder()
                .handler(handler)
                .totalScore(totalScore)
                .skillMatchScore(roundScore(skillMatchScore))
                .loadScore(roundScore(loadScore))
                .slaScore(roundScore(slaScore))
                .ratingScore(roundScore(ratingScore))
                .reasons(Arrays.asList(
                        "技能匹配规则待接入，暂计0分",
                        "当前负载 " + handler.getCurrentLoad() + "/" + handler.getMaxCapacity(),
                        "SLA达成率得分 " + roundScore(slaScore),
                        "历史评分折算得分 " + roundScore(ratingScore)))
                .build();
    }

    /** 剩余容量比例越高，负载得分越高；无有效容量时计0分。 */
    private BigDecimal calculateLoadScore(HandlerProfile handler) {
        if (handler.getMaxCapacity() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal loadRate = BigDecimal.valueOf(Math.max(0, handler.getCurrentLoad()))
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(handler.getMaxCapacity()), 10, RoundingMode.HALF_UP);
        return normalizeScore(HUNDRED.subtract(loadRate));
    }

    /** 空数据计0分，异常数据限制在0-100范围内。 */
    private BigDecimal normalizeScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score.max(BigDecimal.ZERO).min(HUNDRED);
    }

    private BigDecimal roundScore(BigDecimal score) {
        return score.setScale(1, RoundingMode.HALF_UP);
    }
}
