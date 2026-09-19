package com.WorkOrder.sla.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.security.CurrentUserRoleProvider;
import com.WorkOrder.sla.dto.SlaRecordBoardDto;
import com.WorkOrder.sla.service.SlaRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Virgor
 * @date 2026年09月19日 04:30
 * @description SlaRecordController 工单SLA记录控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/sla")
public class SlaRecordController {

    private final SlaRecordService slaRecordService;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;

    @PreAuthorize("hasRole('ADMIN') or hasRole('HANDLER')")
    @GetMapping("/board")
    public Result<PageResult<TicketResponse>> getRealTimeSlaRecords(
                Authentication authentication,
                SlaRecordBoardDto slaRecordBoardDto
    ) {
        Long operatorId = currentUserIdProvider.get(authentication);
        String operatorRole = currentUserRoleProvider.get(authentication);
        return Result.success(slaRecordService.getRealTimeSlaRecords(operatorId, operatorRole, slaRecordBoardDto));
    }

}
