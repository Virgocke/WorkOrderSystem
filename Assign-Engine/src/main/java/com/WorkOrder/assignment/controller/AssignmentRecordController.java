package com.WorkOrder.assignment.controller;

import com.WorkOrder.assignment.dto.AssignmentRecordDto;
import com.WorkOrder.assignment.service.AssignEngineService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.security.CurrentUserRoleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Virgor
 * @date 2026年09月18日 20:12
 * @description 工单分配记录控制器
 */
@RequestMapping("/assignment-records")
@RequiredArgsConstructor
@RestController
public class AssignmentRecordController {

    private final AssignEngineService assignEngineService;
    private final CurrentUserRoleProvider currentUserRoleProvider;
    private final CurrentUserIdProvider currentUserIdProvider;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<PageResult<AssignmentRecordDto>> getAssignmentRecords(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam("ticketId") Long ticketId,
            Authentication authentication
    ) {
        String operatorRole = currentUserRoleProvider.get(authentication);
        Long operatorId = currentUserIdProvider.get(authentication);
        return Result.success(assignEngineService.getAssignmentRecords(
                page, pageSize, ticketId, operatorRole, operatorId));
    }
}
