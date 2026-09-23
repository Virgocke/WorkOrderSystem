package com.WorkOrder.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.skill.dto.SkillApplicationDto;
import com.WorkOrder.skill.service.SkillApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final SkillApplicationService skillApplicationService;

    @PreAuthorize("hasRole('HANDLER')")
    @PostMapping
    public Result<SkillApplication> handlerSkillApplication(
            @Valid @RequestBody SkillApplicationDto skillApplicationDto,
            Authentication authentication) {

        Long handlerId = currentUserIdProvider.get(authentication);
        return skillApplicationService.handlerSkillApplication(skillApplicationDto, handlerId);
    }
}
