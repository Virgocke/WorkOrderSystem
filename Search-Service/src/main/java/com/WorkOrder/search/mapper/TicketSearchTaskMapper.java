package com.WorkOrder.search.mapper;

import com.WorkOrder.search.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 主库任务持久化；修改均由短事务中的共享控制锁保护。
 */
@Mapper
public interface TicketSearchTaskMapper {
    /**
     * 新鲜读取共享状态。
     *
     * @return 工单搜索控制状态
     */
    TicketSearchControlState selectState();
    /**
     * 获取固定控制锁，串行化任务创建、状态和进度提交。
     *
     * @return 工单搜索控制状态
     */
    TicketSearchControlState lockState();
    /**
     * 新鲜读取任务。
     *
     * @param id 搜索任务 ID
     * @return 指定导入任务；不存在时为 null
     */
    TicketSearchImportTask selectTask(@Param("id") Long id);
    /**
     * 查找同一请求首次创建的任务。
     *
     * @param requestId 请求幂等标识
     * @return 该幂等请求首次登记的导入任务；尚未登记时为 null
     */
    TicketSearchImportTask selectByRequest(@Param("requestId") String requestId);
    /**
     * 插入占位任务并取得数据库主键，随后登记受管目标。
     *
     * @param task 任务
     * @return 本次操作影响的记录行数
     */
    int insertTask(TicketSearchImportTask task);
    /**
     * 更新任务，调用方必须持有共享控制锁。
     *
     * @param task 任务
     * @return 本次操作影响的记录行数
     */
    int updateTask(TicketSearchImportTask task);
    /**
     * 更新唯一控制状态。
     *
     * @param state 状态
     * @return 本次操作影响的记录行数
     */
    int updateState(TicketSearchControlState state);
    /**
     * 用数据库时钟领取租约；活跃租约不可抢占。
     *
     * @param id 搜索任务 ID
     * @param owner 本次任务或租约的持有者标识
     * @param token 当前扫描工作者持有的租约令牌；过期或换持有者后不可提交
     * @param seconds 时长，单位为秒
     * @return 更新成功时为 1，条件不匹配时为 0
     */
    int acquireLease(@Param("id") Long id, @Param("owner") String owner,
                     @Param("token") String token, @Param("seconds") int seconds);
    /**
     * 续租只接受未过期的原令牌。
     *
     * @param id 搜索任务 ID
     * @param token 当前扫描工作者持有的租约令牌；过期或换持有者后不可提交
     * @param seconds 时长，单位为秒
     * @return 更新成功时为 1，条件不匹配时为 0
     */
    int renewLease(@Param("id") Long id, @Param("token") String token,
                   @Param("seconds") int seconds);
    /**
     * 校验令牌和数据库时钟，旧工作者不可提交进度。
     *
     * @param id 搜索任务 ID
     * @param token 当前扫描工作者持有的租约令牌；过期或换持有者后不可提交
     * @return 令牌匹配且租约未过期时为 1，否则为 0
     */
    int ownsLease(@Param("id") Long id, @Param("token") String token);
    /**
     * 释放自身租约。
     *
     * @param id 搜索任务 ID
     * @param token 当前扫描工作者持有的租约令牌；过期或换持有者后不可提交
     * @return 更新成功时为 1，条件不匹配时为 0
     */
    int releaseLease(@Param("id") Long id, @Param("token") String token);
    /**
     * 读取独立对账进度。
     *
     * @return 工单搜索对账进度
     */
    TicketSearchReconciliationState selectReconciliation();
    /**
     * 更新对账进度，必须持有共享控制锁。
     *
     * @param state 独立对账进度记录，包含代次、游标和轮次完成事实
     * @return 本次操作影响的记录行数
     */
    int updateReconciliation(TicketSearchReconciliationState state);
    /**
     * 领取对账租约。
     *
     * @param token 当前周期对账工作者持有的独立租约令牌
     * @param seconds 时长，单位为秒
     * @return 更新成功时为 1，条件不匹配时为 0
     */
    int acquireReconciliation(@Param("token") String token, @Param("seconds") int seconds);
    /**
     * 未过期对账令牌的持有验证。
     *
     * @param token 当前周期对账工作者持有的独立租约令牌
     * @return 令牌匹配且租约未过期时为 1，否则为 0
     */
    int ownsReconciliation(@Param("token") String token);
    /**
     * 续租对账。
     *
     * @param token 当前周期对账工作者持有的独立租约令牌
     * @param seconds 时长，单位为秒
     * @return 更新成功时为 1，条件不匹配时为 0
     */
    int renewReconciliation(@Param("token") String token, @Param("seconds") int seconds);
    /**
     * 释放对账租约。
     *
     * @param token 当前周期对账工作者持有的独立租约令牌
     * @return 更新成功时为 1，条件不匹配时为 0
     */
    int releaseReconciliation(@Param("token") String token);
}
