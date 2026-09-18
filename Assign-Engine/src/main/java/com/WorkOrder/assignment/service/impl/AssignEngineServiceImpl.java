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
import com.WorkOrder.model.Result;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.model.assignment.AssignmentWeightsSnapshot;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
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
        // 获取当前的权重配置
        AssignmentWeightsSnapshot weights = configurationService.getCurrentWeights();
        return handlers.stream()
                .map(handler -> buildCandidate(handler, weights))
                .sorted(Comparator.comparing(AssignCandidate::getTotalScore).reversed()
                        .thenComparing(candidate -> candidate.getHandler().getUserId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取工单分配记录。
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
        TicketResponse ticket = getResponseData(ticketFeignClient.getTicketInfo(ticketId),
                SystemExceptionEnum.TICKET_NOT_FOUND);
        Map<Long, UserProfile> handlers = new HashMap<>();
        List<AssignmentRecordDto> records = new ArrayList<>(assignmentRecordList.size());

        // 构建分配记录DTO
        for (AssignmentRecord assignmentRecord : assignmentRecordList) {
            // 获取处理人资料
            Long handlerId = assignmentRecord.getHandlerId();
            UserProfile handler = handlers.computeIfAbsent(handlerId,
                    id -> getResponseData(userFeignClient.getById(id), SystemExceptionEnum.USER_NOT_FOUND));

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

    /** 校验远程响应，保留业务错误码并避免空数据引起空指针异常。 */
    private <T> T getResponseData(Result<T> result, SystemExceptionEnum missingDataError) {
        if (result == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        if (result.getCode() != SystemExceptionEnum.SUCCESS.getCode()) {
            SystemExceptionEnum error = Arrays.stream(SystemExceptionEnum.values())
                    .filter(value -> value.getCode() == result.getCode())
                    .findFirst().orElse(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            throw new SystemException(error);
        }
        if (result.getData() == null) {
            throw new SystemException(missingDataError);
        }
        return result.getData();
    }

    /**
     * 构建候选人得分与推荐原因，分项及综合分均保留一位小数。
     */
    private AssignCandidate buildCandidate(HandlerProfile handler, AssignmentWeightsSnapshot weights) {
        //todo 接入工单分类与所需技能的关联规则，结合处理人技能及熟练度计算技能匹配分，暂计0分。
        BigDecimal skillMatchScore = BigDecimal.ZERO;
        //todo 接入 Redis 实时负载及用户服务的负载维护，目前使用 handler_profiles.current_load。
        BigDecimal loadScore = calculateLoadScore(handler);
        //todo 接入 SLA-Monitor 绩效聚合，目前使用处理人档案中已有的 SLA 达成率。
        BigDecimal slaScore = normalizeScore(handler.getSlaComplianceRate());
        //todo 接入评价模块的历史评分聚合，目前使用处理人档案中已有的平均评分。
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
        // 计算负载率
        BigDecimal loadRate = BigDecimal.valueOf(Math.max(0, handler.getCurrentLoad()))
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(handler.getMaxCapacity()), 10, RoundingMode.HALF_UP);
        return normalizeScore(HUNDRED.subtract(loadRate));
    }

    /** 空数据计0分，异常数据限制在0-100范围内。 */
    private BigDecimal normalizeScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score.max(BigDecimal.ZERO).min(HUNDRED);
    }

    /**
     * 四舍五入保留一位小数。
     * @param score 待四舍五入的分数
     * @return 四舍五入保留一位小数的分数
     */
    private BigDecimal roundScore(BigDecimal score) {
        return score.setScale(1, RoundingMode.HALF_UP);
    }
}
