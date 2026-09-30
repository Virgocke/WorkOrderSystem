package com.WorkOrder.assignment.mapper;

import com.WorkOrder.model.handler.HandlerProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @description 推荐候选人所需的工单与处理人档案查询。
 */
@Mapper
public interface AssignEngineMapper {
    /** 查询工单 ID，供分配记录查询验证工单存在。 */
    Long selectTicketId(@Param("ticketId") Long ticketId);

    /** 查询工单分类；无工单时返回 null。 */
    Long selectTicketCategoryId(@Param("ticketId") Long ticketId);

    /** 查询当前分类直接要求的技能标签 ID。 */
    List<Long> selectCategorySkillIds(@Param("categoryId") Long categoryId);

    /** 查询父分类 ID；用于向上继承最近的技能要求。 */
    Long selectCategoryParentId(@Param("categoryId") Long categoryId);

    /** 查询启用的处理人账号、档案和技能。 */
    List<HandlerProfile> selectEnabledHandlers();
}
