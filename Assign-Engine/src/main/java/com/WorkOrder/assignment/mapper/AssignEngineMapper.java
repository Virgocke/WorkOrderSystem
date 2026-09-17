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

    /** 查询工单是否存在。 */
    Long selectTicketId(@Param("ticketId") Long ticketId);

    /** 查询启用的处理人账号、档案和技能。 */
    List<HandlerProfile> selectEnabledHandlers();
}
