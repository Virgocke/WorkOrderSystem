package com.WorkOrder.assignment.mapper;

import com.WorkOrder.model.handler.HandlerProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 推荐候选人所需的工单与处理人档案查询。
 */
@Mapper
public interface AssignEngineMapper {
    /**
     * 查询工单 ID，供分配记录查询验证工单存在。
     *
     * @param ticketId 工单 ID
     * @return 工单 ID；工单不存在时为 null
     */
    Long selectTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询工单分类；无工单时返回 null。
     *
     * @param ticketId 工单 ID
     * @return 工单分类 ID；工单不存在时为 null
     */
    Long selectTicketCategoryId(@Param("ticketId") Long ticketId);

    /**
     * 查询当前分类直接要求的技能标签 ID。
     *
     * @param categoryId 工单分类 ID
     * @return 长整型数值列表
     */
    List<Long> selectCategorySkillIds(@Param("categoryId") Long categoryId);

    /**
     * 查询父分类 ID；用于向上继承最近的技能要求。
     *
     * @param categoryId 工单分类 ID
     * @return 父分类 ID；分类不存在或没有父分类时为 null
     */
    Long selectCategoryParentId(@Param("categoryId") Long categoryId);

    /**
     * 查询启用的处理人账号、档案和技能。
     *
     * @return 处理人资料列表
     */
    List<HandlerProfile> selectEnabledHandlers();
}
