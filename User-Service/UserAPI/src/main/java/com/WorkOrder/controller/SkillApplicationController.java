package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.security.CurrentUserRoleProvider;
import com.WorkOrder.skill.dto.ReviewSkillApplicationDto;
import com.WorkOrder.skill.dto.SkillApplicationDto;
import com.WorkOrder.skill.service.SkillApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @author Virgor
 * @date 2026年09月23日 17:28
 * @description
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/skill-applications")
public class SkillApplicationController {

    private final CurrentUserIdProvider currentUserIdProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;
    private final SkillApplicationService skillApplicationService;

    @PreAuthorize("hasRole('HANDLER')")
    @PostMapping
    public Result<SkillApplication> handlerSkillApplication(
            @Valid @RequestBody SkillApplicationDto skillApplicationDto,
            Authentication authentication) {

        Long handlerId = currentUserIdProvider.get(authentication);
        return skillApplicationService.handlerSkillApplication(skillApplicationDto, handlerId);
    }

    /** 按接口文档 16.4 分页查询申请；处理人只能看到本人数据。 */
    @PreAuthorize("hasAnyRole('ADMIN', 'HANDLER')")
    @GetMapping
    public Result<PageResult<SkillApplication>> listApplications(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            Authentication authentication) {
        Long currentUserId = currentUserIdProvider.get(authentication);
        String currentUserRole = currentUserRoleProvider.get(authentication);
        return Result.success(skillApplicationService.listApplications(
                page, pageSize, status, currentUserId, currentUserRole));
    }

    /** 按接口文档 16.5 审核申请。 */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<SkillApplication> reviewApplication(
            @PathVariable Long id,
            @Valid @RequestBody ReviewSkillApplicationDto request,
            Authentication authentication) {
        Long reviewerId = currentUserIdProvider.get(authentication);
        return Result.success(skillApplicationService.reviewApplication(id, request, reviewerId));
    }
}
