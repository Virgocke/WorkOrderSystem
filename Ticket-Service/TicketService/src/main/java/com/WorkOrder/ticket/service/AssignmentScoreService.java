package com.WorkOrder.ticket.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.handler.model.AssignmentRecord;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.ticket.feignclient.AssignmentScoreFeignClient;
import com.WorkOrder.utils.ResponseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 将统一派单评分填入手动分配或转派的历史快照。
 */
@Service
@RequiredArgsConstructor
public class AssignmentScoreService {
    private final AssignmentScoreFeignClient assignmentScoreFeignClient;

    /**
     * 填充分配评分信息。
     *
     * @param ticketId 待分配工单 ID
     * @param handlerId 目标处理人用户 ID
     * @param additionalLoad 同一批事务内已分给目标处理人的新增工单数
     * @param record 待写入的分配记录
     */
    public void fill(Long ticketId, Long handlerId, int additionalLoad, AssignmentRecord record) {

        AssignCandidate candidate = ResponseUtils.getResponseData(
                assignmentScoreFeignClient.score(ticketId, handlerId, additionalLoad),
                SystemExceptionEnum.INTERNAL_SERVER_ERROR);

        record.setScore(candidate.getTotalScore());
        record.setSkillMatchScore(candidate.getSkillMatchScore());
        record.setLoadScore(candidate.getLoadScore());
        record.setSlaScore(candidate.getSlaScore());
        record.setRatingScore(candidate.getRatingScore());
    }
}
