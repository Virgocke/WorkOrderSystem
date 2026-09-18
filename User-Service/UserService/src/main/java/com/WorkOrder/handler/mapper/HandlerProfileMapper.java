package com.WorkOrder.handler.mapper;

import com.WorkOrder.handler.model.HandlerProfiles;
import com.WorkOrder.model.handler.HandlerProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 02:39
 * @description
 */
@Mapper
public interface HandlerProfileMapper extends BaseMapper<HandlerProfiles> {

    /**
     * 单次联表查询启用处理人，用户主键用于分配，档案主键不对外作为用户 ID。
     * @return 启用处理人列表
     */
    @Select("SELECT u.id AS user_id, u.real_name, u.username, u.department_id, " +
            "d.name AS department_name, u.status, hp.max_capacity, hp.current_load, " +
            "hp.avg_response_minutes, hp.avg_resolution_minutes, " +
            "hp.sla_compliance_rate, hp.rating_score " +
            "FROM handler_profiles hp INNER JOIN users u ON u.id = hp.user_id " +
            "LEFT JOIN departments d ON d.id = u.department_id " +
            "WHERE u.role = 1 AND u.status = 1 ORDER BY u.id")
    @Results(id = "enabledHandlerOption", value = {
            @Result(column = "user_id", property = "id", id = true),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "real_name", property = "realName"),
            @Result(column = "username", property = "username"),
            @Result(column = "department_id", property = "departmentId"),
            @Result(column = "department_name", property = "departmentName"),
            @Result(column = "status", property = "status"),
            @Result(column = "max_capacity", property = "maxCapacity"),
            @Result(column = "current_load", property = "currentLoad"),
            @Result(column = "avg_response_minutes", property = "avgResponseMinutes"),
            @Result(column = "avg_resolution_minutes", property = "avgResolutionMinutes"),
            @Result(column = "sla_compliance_rate", property = "slaComplianceRate"),
            @Result(column = "rating_score", property = "ratingScore")
    })
    List<HandlerProfile> selectEnabledHandlerOptions();
}
