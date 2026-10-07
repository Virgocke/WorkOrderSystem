package com.WorkOrder.assignment.service.impl;

import com.WorkOrder.assignment.dto.AssignmentRecordDto;
import com.WorkOrder.assignment.feignclient.TicketFeignClient;
import com.WorkOrder.assignment.feignclient.UserFeignClient;
import com.WorkOrder.assignment.mapper.AssignEngineMapper;
import com.WorkOrder.assignment.mapper.AssignmentRecordMapper;
import com.WorkOrder.assignment.model.AssignmentRecord;
import com.WorkOrder.assignment.service.AssignEngineService;
import com.WorkOrder.assignment.service.ConfigurationService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.model.assignment.AssignmentWeightsSnapshot;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.handler.HandlerSkillItem;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.utils.ResponseUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 推荐候选人服务实现，按技能、负载、SLA和历史评分计算综合分。
 */
@Service
@RequiredArgsConstructor
public class AssignEngineServiceImpl implements AssignEngineService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWENTY = new BigDecimal("20");

    private final AssignmentRecordMapper assignmentRecordMapper;
    private final AssignEngineMapper assignEngineMapper;
    private final ConfigurationService configurationService;
    private final TicketFeignClient ticketFeignClient;
    private final UserFeignClient userFeignClient;

    /**
     * 根据工单ID推荐处理人。
     *
     * @param ticketId 待推荐工单ID
     * @param operatorRole 当前登录用户角色
     * @return 处理人推荐列表
     */
    @Override
    public List<AssignCandidate> recommend(Long ticketId, String operatorRole) {
        // 当前角色来自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        return recommendCandidates(ticketId, null, 0);
    }

    /**
     * 创建事件使用同一评分规则，跳过档案负载已达到上限的处理人。
     *
     * @param ticketId 新建工单 ID
     * @return 最高分候选人；无人可用时返回 null
     */
    @Override
    public AssignCandidate recommendForSystem(Long ticketId) {
        return recommendCandidates(ticketId, null, 0).stream()
                .filter(candidate -> candidate.getHandler().getCurrentLoad()
                        < candidate.getHandler().getMaxCapacity())
                .findFirst().orElse(null);
    }

    /**
     * 按同一评分规则生成手动派单快照；批量派单可传入本事务内已分配的数量。
     *
     * @param ticketId 工单 ID
     * @param handlerId 处理人用户 ID
     * @param additionalLoad 本批次内额外分配的工单数量
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @return 分配Candidate
     */
    @Override
    public AssignCandidate scoreForHandler(Long ticketId, Long handlerId,
                                           int additionalLoad, String operatorRole) {

        if (!("ADMIN".equals(operatorRole) || "HANDLER".equals(operatorRole))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (handlerId == null || handlerId <= 0 || additionalLoad < 0 || additionalLoad > 1000) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        return recommendCandidates(ticketId, handlerId, additionalLoad)
                .stream()
                .filter(candidate -> handlerId.equals(candidate.getHandler().getUserId()))
                .findFirst()
                .orElseThrow(() -> new SystemException(SystemExceptionEnum.USER_NOT_FOUND));
    }

    /**
     * 查询并按当前权重给所有启用处理人评分。
     *
     * @param ticketId 工单 ID
     * @param adjustedHandlerId adjusted处理人 ID
     * @param additionalLoad 本批次内额外分配的工单数量
     * @return 按综合得分降序排列的候选人
     */
    private List<AssignCandidate> recommendCandidates(Long ticketId, Long adjustedHandlerId, int additionalLoad) {
        if (ticketId == null || ticketId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        Long categoryId = assignEngineMapper.selectTicketCategoryId(ticketId);
        if (categoryId == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        List<Long> requiredSkillIds = requiredSkillIds(categoryId);

        List<HandlerProfile> handlers = assignEngineMapper.selectEnabledHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyList();
        }
        // 获取当前的权重配置
        AssignmentWeightsSnapshot weights = configurationService.getCurrentWeights();
        return handlers
                .stream()
                .map(handler -> {
                    // 调整处理人负载
                    if (handler.getUserId().equals(adjustedHandlerId)) {
                        handler.setCurrentLoad(handler.getCurrentLoad() + additionalLoad);
                    }
                    // 计算候选人评分
                    return buildCandidate(handler, requiredSkillIds, weights);
                })
                .sorted(Comparator.comparing(AssignCandidate::getTotalScore).reversed()
                        .thenComparing(candidate -> candidate.getHandler().getUserId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取工单分配记录。
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @param ticketId 工单ID
     * @param operatorRole 操作员角色
     * @param operatorId 操作员ID
     * @return 分配记录分页结果
     */
    @Override
    public PageResult<AssignmentRecordDto> getAssignmentRecords(int page,
                                                               int pageSize,
                                                               Long ticketId,
                                                               String operatorRole,
                                                               Long operatorId) {

        // 当前角色来自后端认证信息，服务层再次校验管理员权限。
        if (!"ADMIN".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        if (page < 1 || pageSize < 1 || ticketId == null || ticketId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (assignEngineMapper.selectTicketId(ticketId) == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }

        // 分页查询分配记录
        Page<AssignmentRecord> assignmentRecordPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<AssignmentRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssignmentRecord::getTicketId, ticketId)
                .orderByDesc(AssignmentRecord::getCreatedAt)
                .orderByDesc(AssignmentRecord::getId);
        Page<AssignmentRecord> recordPage = assignmentRecordMapper.selectPage(assignmentRecordPage, queryWrapper);
        List<AssignmentRecord> assignmentRecordList = recordPage.getRecords();
        if (assignmentRecordList.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), recordPage.getTotal(), page, pageSize);
        }

        // 同一工单的详情只查询一次；重复接手的处理人在本次请求内复用用户资料。
        TicketResponse ticket = ResponseUtils.getResponseData(ticketFeignClient.getTicketInfo(ticketId),
                SystemExceptionEnum.TICKET_NOT_FOUND);
        Map<Long, UserProfile> handlers = new HashMap<>();
        List<AssignmentRecordDto> records = new ArrayList<>(assignmentRecordList.size());

        // 构建分配记录DTO
        for (AssignmentRecord assignmentRecord : assignmentRecordList) {
            // 获取处理人资料
            Long handlerId = assignmentRecord.getHandlerId();
            UserProfile handler = handlers.computeIfAbsent(handlerId,
                    id -> ResponseUtils.getResponseData(userFeignClient.getById(id), SystemExceptionEnum.USER_NOT_FOUND));

            // 每次分配或转派保留一条独立记录，不按处理人去重。
            AssignmentRecordDto dto = new AssignmentRecordDto();
            dto.setId(assignmentRecord.getId());
            dto.setTicketId(assignmentRecord.getTicketId());
            dto.setTicketNo(ticket.getTicketNo());
            dto.setTicketTitle(ticket.getTitle());
            dto.setHandlerId(handlerId);
            dto.setHandlerName(handler.getRealName());
            dto.setScore(assignmentRecord.getScore());
            dto.setAssignedBy(assignmentRecord.getAssignedBy());
            dto.setSkillMatchScore(assignmentRecord.getSkillMatchScore());
            dto.setLoadScore(assignmentRecord.getLoadScore());
            dto.setSlaScore(assignmentRecord.getSlaScore());
            dto.setRatingScore(assignmentRecord.getRatingScore());
            dto.setCreatedAt(assignmentRecord.getCreatedAt());
            records.add(dto);
        }

        return new PageResult<>(records, recordPage.getTotal(), page, pageSize);
    }

    /**
     * 构建候选人得分与推荐原因，分项及综合分均保留一位小数。
     *
     * @param handler 处理人
     * @param requiredSkillIds required技能 ID 集合
     * @param weights 权重
     * @return 分配Candidate
     */
    private AssignCandidate buildCandidate(HandlerProfile handler, List<Long> requiredSkillIds,
                                           AssignmentWeightsSnapshot weights) {
        BigDecimal skillMatchScore = calculateSkillScore(handler, requiredSkillIds);
        // 由 tickets 当前在办状态实时计数，不使用档案中的冗余 current_load。
        BigDecimal loadScore = calculateLoadScore(handler);
        // SLA 和评价均由解决人、评价归属快照的事实记录按需聚合。
        BigDecimal slaScore = normalizeScore(handler.getSlaComplianceRate());
        BigDecimal ratingScore = normalizeScore(handler.getRatingScore() == null
                ? BigDecimal.ZERO : handler.getRatingScore().multiply(TWENTY));

        // 所有候选人使用本次推荐开始时取得的同一份权重快照。
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
                .weights(weights)
                .reasons(Arrays.asList(
                        requiredSkillIds.isEmpty() ?
                                "分类未配置所需技能，按中性50分" :
                                "所需技能 " + requiredSkillIds.size() + " 项，匹配得分 " + roundScore(skillMatchScore),
                        "当前负载 " + handler.getCurrentLoad() + "/" + handler.getMaxCapacity(),
                        "SLA达成率得分 " + roundScore(slaScore),
                        "历史评分折算得分 " + roundScore(ratingScore)))
                .build();
    }

    /**
     * 沿分类树向上寻找最近一层配置的技能要求，避免子分类重复配置。
     *
     * @param categoryId 工单分类 ID
     * @return 长整型数值列表
     */
    private List<Long> requiredSkillIds(Long categoryId) {
        Set<Long> visited = new HashSet<>();
        Long current = categoryId;
        while (current != null && visited.add(current)) {
            List<Long> skillIds = assignEngineMapper.selectCategorySkillIds(current);
            if (skillIds != null && !skillIds.isEmpty()) {
                return skillIds;
            }
            current = assignEngineMapper.selectCategoryParentId(current);
        }
        return Collections.emptyList();
    }

    /**
     * 各项要求等权；单项熟练度 1 至 5 对应 20 至 100 分，缺项计 0 分。
     *
     * @param handler 处理人
     * @param requiredSkillIds required技能 ID 集合
     * @return 高精度数值
     */
    private BigDecimal calculateSkillScore(HandlerProfile handler, List<Long> requiredSkillIds) {
        if (requiredSkillIds.isEmpty()) {
            return new BigDecimal("50");
        }
        int proficiencySum = 0;
        for (Long requiredSkillId : requiredSkillIds) {
            int proficiency = handler.getSkills().stream()
                    .filter(skill -> requiredSkillId.equals(skill.getSkillId()))
                    .mapToInt(HandlerSkillItem::getProficiency)
                    .max().orElse(0);
            proficiencySum += Math.max(0, Math.min(5, proficiency));
        }
        return BigDecimal.valueOf(proficiencySum).multiply(TWENTY)
                .divide(BigDecimal.valueOf(requiredSkillIds.size()), 1, RoundingMode.HALF_UP);
    }

    /**
     * 剩余容量比例越高，负载得分越高；无有效容量时计0分。
     *
     * @param handler 处理人
     * @return 高精度数值
     */
    private BigDecimal calculateLoadScore(HandlerProfile handler) {
        if (handler.getMaxCapacity() <= 0) {
            return BigDecimal.ZERO;
        }
        // 计算负载率
        BigDecimal loadRate = BigDecimal.valueOf(Math.max(0, handler.getCurrentLoad()))
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(handler.getMaxCapacity()), 10, RoundingMode.HALF_UP);
        return normalizeScore(HUNDRED.subtract(loadRate));
    }

    /**
     * 空数据计0分，异常数据限制在0-100范围内。
     *
     * @param score 评分
     * @return 高精度数值
     */
    private BigDecimal normalizeScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score.max(BigDecimal.ZERO).min(HUNDRED);
    }

    /**
     * 四舍五入保留一位小数。
     *
     * @param score 待四舍五入的分数
     * @return 四舍五入保留一位小数的分数
     */
    private BigDecimal roundScore(BigDecimal score) {
        return score.setScale(1, RoundingMode.HALF_UP);
    }
}
