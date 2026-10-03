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

    /** 将当前操作人姓名筛选转换为工单日志的操作人 ID；系统操作统一为 0。 */
    List<Long> selectTicketOperatorIds(@Param("q") AuditQuery query);

    /** 将当前操作人姓名筛选转换为配置日志的操作人 ID；系统操作统一为 0。 */
    List<Long> selectConfigurationOperatorIds(@Param("q") AuditQuery query);

    /** 按当前工单编号取得 ID，供 ES 在分页前应用编号筛选。 */
    List<Long> selectTicketIds(@Param("q") AuditQuery query);

    /** 使用源库配置键等值语义取得配置日志 ID，保留 MySQL 的大小写和排序规则。 */
    List<Long> selectConfigurationIds(@Param("q") AuditQuery query);

    /** 按当前 ES 页的跨表唯一 ID 回表，复核其他条件，不进行第二次分页。 */
    List<TicketAuditItem> selectTicketsByIds(@Param("q") AuditQuery query,
                                           @Param("operationIds") List<Long> operationIds,
                                           @Param("statusIds") List<Long> statusIds);

    /** 按当前 ES 页的配置日志 ID 回表，复核其他条件，不进行第二次分页。 */
    List<ConfigurationAuditItem> selectConfigurationsByIds(@Param("q") AuditQuery query,
                                                         @Param("ids") List<Long> ids);
}
