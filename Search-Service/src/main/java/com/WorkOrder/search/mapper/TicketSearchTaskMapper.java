package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 主库任务持久化；修改均由短事务中的共享控制锁保护。 */
@Mapper
public interface TicketSearchTaskMapper {
    /** 新鲜读取共享状态。 */
    TicketSearchControlState selectState();
    /** 获取固定控制锁，串行化任务创建、状态和进度提交。 */
    TicketSearchControlState lockState();
    /** 新鲜读取任务。 */
    TicketSearchImportTask selectTask(@Param("id") Long id);
    /** 查找同一请求首次创建的任务。 */
    TicketSearchImportTask selectByRequest(@Param("requestId") String requestId);
    /** 插入占位任务并取得数据库主键，随后登记受管目标。 */
    int insertTask(TicketSearchImportTask task);
    /** 更新任务，调用方必须持有共享控制锁。 */
    int updateTask(TicketSearchImportTask task);
    /** 更新唯一控制状态。 */
    int updateState(TicketSearchControlState state);
    /** 用数据库时钟领取租约；活跃租约不可抢占。 */
    int acquireLease(@Param("id") Long id, @Param("owner") String owner,
                     @Param("token") String token, @Param("seconds") int seconds);
    /** 续租只接受未过期的原令牌。 */
    int renewLease(@Param("id") Long id, @Param("token") String token,
                   @Param("seconds") int seconds);
    /** 校验令牌和数据库时钟，旧工作者不可提交进度。 */
    int ownsLease(@Param("id") Long id, @Param("token") String token);
    /** 释放自身租约。 */
    int releaseLease(@Param("id") Long id, @Param("token") String token);
    /** 读取独立对账进度。 */
    TicketSearchReconciliationState selectReconciliation();
    /** 更新对账进度，必须持有共享控制锁。 */
    int updateReconciliation(TicketSearchReconciliationState state);
    /** 领取对账租约。 */
    int acquireReconciliation(@Param("token") String token, @Param("seconds") int seconds);
    /** 未过期对账令牌的持有验证。 */
    int ownsReconciliation(@Param("token") String token);
    /** 续租对账。 */
    int renewReconciliation(@Param("token") String token, @Param("seconds") int seconds);
    /** 释放对账租约。 */
    int releaseReconciliation(@Param("token") String token);
}
