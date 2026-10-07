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
 * @description 提供技能变更申请的提交、查询与管理员审核接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/skill-applications")
public class SkillApplicationController {

    private final CurrentUserIdProvider currentUserIdProvider;
    private final CurrentUserRoleProvider currentUserRoleProvider;
    private final SkillApplicationService skillApplicationService;

    /**
     * 处理人申请技能
     *
     * @param skillApplicationDto 申请数据
     * @param authentication 当前用户认证信息
     * @return 处理结果
     */
    @PreAuthorize("hasRole('HANDLER')")
    @PostMapping
    public Result<SkillApplication> handlerSkillApplication(
            @Valid @RequestBody SkillApplicationDto skillApplicationDto,
            Authentication authentication) {

        Long handlerId = currentUserIdProvider.get(authentication);
        return skillApplicationService.handlerSkillApplication(skillApplicationDto, handlerId);
    }

    /**
     * 按接口文档 16.4 分页查询申请；处理人只能看到本人数据。
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @param status 状态
     * @param authentication 当前用户认证信息
     * @return 分页查询结果
     */
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

    /**
     * 详情权限由服务再次限定为本人或管理员。
     *
     * @param id 技能申请 ID
     * @param authentication 当前已认证的登录信息
     * @return 统一响应，包含技能申请
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HANDLER')")
    @GetMapping("/{id}")
    public Result<SkillApplication> getApplication(@PathVariable Long id, Authentication authentication) {
        return Result.success(skillApplicationService.getApplication(id,
                currentUserIdProvider.get(authentication), currentUserRoleProvider.get(authentication)));
    }

    /**
     * 按接口文档 16.5 审核申请。
     *
     * @param id 技能申请 ID
     * @param request 审核技能申请请求数据
     * @param authentication 当前已认证的登录信息
     * @return 统一响应，包含技能申请
     */
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
