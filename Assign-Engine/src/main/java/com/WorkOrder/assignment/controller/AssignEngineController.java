package com.WorkOrder.assignment.controller;

import com.WorkOrder.assignment.service.AssignEngineService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.assignment.AssignCandidate;
import com.WorkOrder.security.CurrentUserRoleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 智能分配接口。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/assign-engine")
public class AssignEngineController {

    private final AssignEngineService assignEngineService;
    private final CurrentUserRoleProvider currentUserRoleProvider;

    /**
     * 获取工单推荐候选人。
     *
     * @param ticketId 工单 ID
     * @param authentication 当前用户认证信息
     * @return 按综合得分排序的候选人列表
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/recommend")
    public Result<List<AssignCandidate>> recommend(
            @RequestParam("ticketId") Long ticketId,
            Authentication authentication) {
        String operatorRole = currentUserRoleProvider.get(authentication);
        return Result.success(assignEngineService.recommend(ticketId, operatorRole));
    }

    /**
     * 手动派单和转派读取与系统推荐一致的分项分数。
     *
     * @param ticketId 工单 ID
     * @param handlerId 处理人用户 ID
     * @param additionalLoad 本批次内额外分配的工单数量
     * @param authentication 当前已认证的登录信息
     * @return 统一响应，包含分配Candidate
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HANDLER')")
    @GetMapping("/score")
    public Result<AssignCandidate> score(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("handlerId") Long handlerId,
            @RequestParam(value = "additionalLoad", defaultValue = "0") int additionalLoad,
            Authentication authentication) {
        String operatorRole = currentUserRoleProvider.get(authentication);
        return Result.success(assignEngineService
                .scoreForHandler(ticketId, handlerId, additionalLoad, operatorRole));
    }
}
