package com.WorkOrder.dashboard.mapper;

import com.WorkOrder.dashboard.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 只读审计查询。 */
@Mapper
public interface AuditMapper {
    /** 合并操作日志与状态历史后稳定排序分页。 */
    List<TicketAuditItem> selectTickets(@Param("q") AuditQuery query);
    /** 使用与列表相同的筛选条件统计工单审计条数。 */
    long countTickets(@Param("q") AuditQuery query);
    /** 分页查询配置变更的前后值及当前操作人姓名。 */
    List<ConfigurationAuditItem> selectConfigurations(@Param("q") AuditQuery query);
    /** 使用与列表相同的筛选条件统计配置审计条数。 */
    long countConfigurations(@Param("q") AuditQuery query);
}
