package com.WorkOrder.assignment.service;

import com.WorkOrder.assignment.dto.AssignmentRecordDto;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.model.page.PageResult;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 智能分配引擎服务。
 */
public interface AssignEngineService {

    /**
     * 获取推荐候选人。
     *
     * @param ticketId 待推荐工单ID
     * @param operatorRole 当前登录用户角色
     * @return 按综合得分降序排列的候选人
     */
    List<AssignCandidate> recommend(Long ticketId, String operatorRole);

    /**
     * 供创建事件消费者取得仍有容量的最高分候选人。
     *
     * @param ticketId 新建工单 ID
     * @return 最高分且仍有容量的处理人；没有候选人时返回 null
     */
    AssignCandidate recommendForSystem(Long ticketId);

    /**
     * 用推荐同一算法计算指定工单和处理人的派单分数。
     *
     * @param ticketId 工单 ID
     * @param handlerId 处理人用户 ID
     * @param additionalLoad 本批次内额外分配的工单数量
     * @param operatorRole 当前登录角色，由后端认证信息取得
     * @return 分配Candidate
     */
    AssignCandidate scoreForHandler(Long ticketId, Long handlerId, int additionalLoad, String operatorRole);

    /**
     * 获取工单分配记录。
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @param ticketId 工单ID
     * @param operatorRole 操作员角色
     * @param operatorId 操作员ID
     * @return 工单分配记录及符合条件的总条数
     */
    PageResult<AssignmentRecordDto> getAssignmentRecords(int page, int pageSize, Long ticketId, String operatorRole, Long operatorId);
}
