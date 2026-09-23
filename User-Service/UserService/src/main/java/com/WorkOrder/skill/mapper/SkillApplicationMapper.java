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
 * @description
 */
@Mapper
public interface SkillApplicationMapper extends BaseMapper<SkillApplicationRecord> {

    /** 分页查询申请，并联表返回申请人和技能名称。 */
    Page<SkillApplication> selectApplicationPage(Page<SkillApplication> page,
                                                 @Param("status") String status,
                                                 @Param("handlerId") Long handlerId);

    /** 查询单条申请的前端响应视图。 */
    SkillApplication selectApplicationById(@Param("id") Long id);

    /** 只允许待审核申请被处理，避免并发重复审核。 */
    int reviewPendingApplication(@Param("id") Long id,
                                 @Param("status") String status,
                                 @Param("reviewerId") Long reviewerId,
                                 @Param("reviewReason") String reviewReason);
}
