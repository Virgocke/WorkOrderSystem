package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.AuditSearchDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 按来源主键读取已经提交的审计日志，不读取会随用户或工单修改的展示字段。 */
@Mapper
public interface AuditIndexSourceMapper {

    /** 按主键升序读取一批操作日志，供搜索投影重复写入。 */
    List<AuditSearchDocument> selectOperations(@Param("afterId") long afterId,
                                              @Param("limit") int limit);

    /** 读取原审计列表可见的状态历史，保持操作名称与状态描述的拼接规则。 */
    List<AuditSearchDocument> selectStatuses(@Param("afterId") long afterId,
                                            @Param("limit") int limit);

    /** 按主键升序读取一批配置变更日志，保留修改前后的原始值。 */
    List<AuditSearchDocument> selectConfigurations(@Param("afterId") long afterId,
                                                  @Param("limit") int limit);
}
