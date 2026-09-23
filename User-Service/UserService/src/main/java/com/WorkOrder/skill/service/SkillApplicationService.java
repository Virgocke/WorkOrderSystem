package com.WorkOrder.skill.service;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.skill.dto.ReviewSkillApplicationDto;
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

    /**
     * 按角色查询技能调整申请。管理员可查看全部，处理人只能查看本人申请。
     */
    PageResult<SkillApplication> listApplications(int page,
                                                  int pageSize,
                                                  String status,
                                                  Long currentUserId,
                                                  String currentUserRole);

    /** 管理员审核申请；审核通过时同步更新处理人技能。 */
    SkillApplication reviewApplication(Long id,
                                       ReviewSkillApplicationDto request,
                                       Long reviewerId);
}
