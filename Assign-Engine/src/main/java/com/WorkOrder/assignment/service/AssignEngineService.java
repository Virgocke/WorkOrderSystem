package com.WorkOrder.assignment.service;

import com.WorkOrder.model.assignment.AssignCandidate;

import java.util.List;

/**
 * @description 智能分配引擎服务。
 */
public interface AssignEngineService {

    /**
     * 获取推荐候选人。
     * @param ticketId 待推荐工单ID
     * @param operatorRole 当前登录用户角色
     * @return 按综合得分降序排列的候选人
     */
    List<AssignCandidate> recommend(Long ticketId, String operatorRole);
}
