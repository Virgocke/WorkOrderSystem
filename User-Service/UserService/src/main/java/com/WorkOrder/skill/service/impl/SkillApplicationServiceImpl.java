package com.WorkOrder.skill.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.handler.mapper.HandlerProfileMapper;
import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.skill.dto.ReviewSkillApplicationDto;
import com.WorkOrder.skill.dto.SkillApplicationDto;
import com.WorkOrder.skill.mapper.SkillApplicationMapper;
import com.WorkOrder.skill.messaging.SkillApplicationReviewedPublisher;
import com.WorkOrder.skill.mapper.SkillTagMapper;
import com.WorkOrder.skill.model.SkillApplicationRecord;
import com.WorkOrder.skill.service.SkillApplicationService;
import com.WorkOrder.user.mapper.HandlerSkillMapper;
import com.WorkOrder.user.model.HandlerSkill;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年09月23日 17:38
 * @description 技能申请服务实现
 */
@Service
@RequiredArgsConstructor
public class SkillApplicationServiceImpl implements SkillApplicationService {

    private static final Set<String> APPLICATION_STATUSES = new HashSet<>(
            Arrays.asList("PENDING", "APPROVED", "REJECTED"));

    /** 审核成功后在同一事务写入结果事件。 */
    private final SkillApplicationReviewedPublisher reviewedPublisher;

    private final SkillApplicationMapper skillApplicationMapper;
    private final SkillTagMapper skillTagMapper;
    private final HandlerProfileMapper handlerProfileMapper;
    private final HandlerSkillMapper handlerSkillMapper;

    /**
     * 处理技能申请，包括添加或移除技能申请
     * @param skillApplicationDto 技能申请dto
     * @param handlerId 处理人id
     * @return 处理结果
     */
    @Override
    @Transactional
    public Result<SkillApplication> handlerSkillApplication(SkillApplicationDto skillApplicationDto, Long handlerId) {
        validateNewApplication(skillApplicationDto, handlerId);

        SkillApplicationRecord skillApplicationRecord = new SkillApplicationRecord();
        skillApplicationRecord.setSkillTagId(skillApplicationDto.getSkillId());
        skillApplicationRecord.setHandlerId(handlerId);
        if ("REMOVE".equals(skillApplicationDto.getType())) {
            skillApplicationRecord.setProficiency(null);
        } else {
            skillApplicationRecord.setProficiency(skillApplicationDto.getProficiency());
        }
        skillApplicationRecord.setType(skillApplicationDto.getType());
        skillApplicationRecord.setReason(skillApplicationDto.getReason());

        int insert = skillApplicationMapper.insert(skillApplicationRecord);
        if (insert < 1) {
            throw new SystemException(SystemExceptionEnum.APPLICATION_FAILED);
        }

        SkillApplication application = skillApplicationMapper.selectApplicationById(
                skillApplicationRecord.getId());
        if (application == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        return Result.success(application);
    }

    /** 按接口文档 16.4 根据角色限定数据范围并分页查询。 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<SkillApplication> listApplications(int page,
                                                         int pageSize,
                                                         String status,
                                                         Long currentUserId,
                                                         String currentUserRole) {
        if (page < 1 || pageSize < 1 || currentUserId == null || currentUserId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        String normalizedStatus = normalizeStatus(status);
        Long handlerId;
        if ("ADMIN".equals(currentUserRole)) {
            handlerId = null;
        } else if ("HANDLER".equals(currentUserRole)) {
            handlerId = currentUserId;
        } else {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        Page<SkillApplication> result = skillApplicationMapper.selectApplicationPage(
                new Page<>(page, pageSize), normalizedStatus, handlerId);
        List<SkillApplication> records = result == null || result.getRecords() == null
                ? Collections.emptyList() : result.getRecords();
        long total = result == null ? 0L : result.getTotal();
        return new PageResult<>(records, total, page, pageSize);
    }

    /** 按接口文档 16.5 审核申请，并在批准时同步处理人技能。 */
    @Override
    @Transactional
    public SkillApplication reviewApplication(Long id,
                                              ReviewSkillApplicationDto request,
                                              Long reviewerId) {
        validateReview(id, request, reviewerId);

        SkillApplicationRecord record = skillApplicationMapper.selectById(id);
        if (record == null) {
            throw new NoSuchElementException("申请不存在");
        }

        String reviewReason = normalizeReason(request.getReason());
        int affected = skillApplicationMapper.reviewPendingApplication(
                id, request.getStatus(), reviewerId, reviewReason);
        if (affected != 1) {
            throw new SystemException("该申请已处理");
        }

        if ("APPROVED".equals(request.getStatus())) {
            applyApprovedApplication(record);
        }



        SkillApplication reviewed = skillApplicationMapper.selectApplicationById(id);
        if (reviewed == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        reviewedPublisher.publish(reviewed);
        return reviewed;
    }

    /** 查询申请详情；隐藏其他处理人的申请是否存在。 */
    @Override
    @Transactional(readOnly = true)
    public SkillApplication getApplication(Long id, Long userId, String role) {
        if (id == null || id <= 0 || userId == null || userId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (!"ADMIN".equals(role) && !"HANDLER".equals(role)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        SkillApplication application = skillApplicationMapper.selectApplicationById(id);
        if (application == null || (!"ADMIN".equals(role) && !userId.equals(application.getHandlerId()))) {
            throw new NoSuchElementException("申请不存在");
        }
        return application;
    }

    /** 校验并规范提交申请所需字段，避免非 Web 调用绕过 Bean Validation。 */
    private void validateNewApplication(SkillApplicationDto request, Long handlerId) {
        if (request == null || handlerId == null || handlerId <= 0
                || request.getSkillId() == null || request.getSkillId() <= 0
                || !("ADD".equals(request.getType())
                || "ADJUST".equals(request.getType())
                || "REMOVE".equals(request.getType()))) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (!"REMOVE".equals(request.getType())
                && (request.getProficiency() == null
                || request.getProficiency() < 1
                || request.getProficiency() > 5)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (request.getReason() != null && request.getReason().length() > 300) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        if (skillTagMapper.selectById(request.getSkillId()) == null) {
            throw new NoSuchElementException("技能不存在");
        }
    }

    /** 应用批准结果，行为与前端 Mock 保持一致：调整缺失技能时创建，移除缺失技能时忽略。 */
    private void applyApprovedApplication(SkillApplicationRecord application) {
        HandlerProfiles profile = handlerProfileMapper.selectOne(
                new QueryWrapper<HandlerProfiles>().eq("user_id", application.getHandlerId()));
        if (profile == null) {
            throw new NoSuchElementException("处理人档案不存在");
        }

        HandlerSkill existing = handlerSkillMapper.selectOne(
                new QueryWrapper<HandlerSkill>()
                        .eq("handler_id", profile.getId())
                        .eq("skill_tag_id", application.getSkillTagId()));

        switch (application.getType()) {
            case "ADD":
                if (existing == null) {
                    insertHandlerSkill(profile.getId(), application);
                }
                break;
            case "ADJUST":
                requireValidProficiency(application.getProficiency());
                if (existing == null) {
                    insertHandlerSkill(profile.getId(), application);
                } else {
                    existing.setProficiency(application.getProficiency());
                    if (handlerSkillMapper.updateById(existing) != 1) {
                        throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
                    }
                }
                break;
            case "REMOVE":
                if (existing != null && handlerSkillMapper.deleteById(existing.getId()) != 1) {
                    throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
                }
                break;
            default:
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /** 新增处理人技能关联。 */
    private void insertHandlerSkill(Long profileId, SkillApplicationRecord application) {
        requireValidProficiency(application.getProficiency());
        HandlerSkill skill = new HandlerSkill();
        skill.setHandlerId(profileId);
        skill.setSkillTagId(application.getSkillTagId());
        skill.setProficiency(application.getProficiency());
        if (handlerSkillMapper.insert(skill) != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /** 校验审核参数，服务被直接调用时也保持接口约束。 */
    private void validateReview(Long id, ReviewSkillApplicationDto request, Long reviewerId) {
        if (id == null || id <= 0 || reviewerId == null || reviewerId <= 0
                || request == null
                || !("APPROVED".equals(request.getStatus())
                || "REJECTED".equals(request.getStatus()))
                || (request.getReason() != null && request.getReason().length() > 300)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }

    /** 空状态表示不筛选，否则只接受文档定义的三种状态。 */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!APPLICATION_STATUSES.contains(normalized)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        return normalized;
    }

    /** 空白审核理由按未填写处理。 */
    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    /** ADD、ADJUST 的持久化申请必须具备有效熟练度。 */
    private void requireValidProficiency(Integer proficiency) {
        if (proficiency == null || proficiency < 1 || proficiency > 5) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
    }
}
