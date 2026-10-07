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
     *
     * @param skillApplicationDto 技能申请dto
     * @param handlerId 处理人id
     * @return 处理结果
     */
    Result<SkillApplication> handlerSkillApplication(@Valid SkillApplicationDto skillApplicationDto, Long handlerId);

    /**
     * 按角色查询技能调整申请。管理员可查看全部，处理人只能查看本人申请。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param status 状态筛选或更新值
     * @param currentUserId 当前用户 ID
     * @param currentUserRole 当前用户角色
     * @return 技能申请的分页结果
     */
    PageResult<SkillApplication> listApplications(int page,
                                                  int pageSize,
                                                  String status,
                                                  Long currentUserId,
                                                  String currentUserRole);

    /**
     * 管理员或原申请人查看申请详情。
     *
     * @param id 技能申请 ID
     * @param userId 用户 ID
     * @param role 角色筛选或校验值
     * @return 技能申请
     */
    SkillApplication getApplication(Long id, Long userId, String role);

    /**
     * 管理员审核申请；审核通过时同步更新处理人技能。
     *
     * @param id 技能申请 ID
     * @param request 审核技能申请请求数据
     * @param reviewerId reviewer ID
     * @return 技能申请
     */
    SkillApplication reviewApplication(Long id,
                                       ReviewSkillApplicationDto request,
                                       Long reviewerId);
}
