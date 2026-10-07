package com.WorkOrder.skill.mapper;

import com.WorkOrder.model.handler.SkillApplication;
import com.WorkOrder.skill.model.SkillApplicationRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author Virgor
 * @date 2026年09月23日 17:38
 * @description 持久化技能变更申请与审核信息。
 */
@Mapper
public interface SkillApplicationMapper extends BaseMapper<SkillApplicationRecord> {

    /**
     * 分页查询申请，并联表返回申请人和技能名称。
     *
     * @param page 页码，从 1 开始
     * @param status 状态筛选或更新值
     * @param handlerId 处理人用户 ID
     * @return 技能申请的分页结果
     */
    Page<SkillApplication> selectApplicationPage(Page<SkillApplication> page,
                                                 @Param("status") String status,
                                                 @Param("handlerId") Long handlerId);

    /**
     * 查询单条申请的前端响应视图。
     *
     * @param id 技能申请 ID
     * @return 技能申请
     */
    SkillApplication selectApplicationById(@Param("id") Long id);

    /**
     * 只允许待审核申请被处理，避免并发重复审核。
     *
     * @param id 技能申请 ID
     * @param status 状态筛选或更新值
     * @param reviewerId reviewer ID
     * @param reviewReason 审核原因
     * @return 待审核记录的条件更新行数；状态已变化时为 0
     */
    int reviewPendingApplication(@Param("id") Long id,
                                 @Param("status") String status,
                                 @Param("reviewerId") Long reviewerId,
                                 @Param("reviewReason") String reviewReason);
}
