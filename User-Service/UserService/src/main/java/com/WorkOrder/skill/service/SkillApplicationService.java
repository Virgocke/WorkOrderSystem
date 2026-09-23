package com.WorkOrder.skill.service;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.skill.dto.SkillApplicationDto;

import javax.validation.Valid;

/**
 * @author Virgor
 * @date 2026年09月23日 17:37
 * @description 技能申请服务
 */
public interface SkillApplicationService {
    /**
     * 处理技能申请
     * @param skillApplicationDto 技能申请dto
     * @param handlerId 处理人id
     * @return 处理结果
     */
    Result<SkillApplication> handlerSkillApplication(@Valid SkillApplicationDto skillApplicationDto, Long handlerId);
}
