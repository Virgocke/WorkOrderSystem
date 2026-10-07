package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.dto.EmailDeliveryAdminQuery;
import com.WorkOrder.notification.model.EmailDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员邮件查询及人工重排，不参与 SMTP 调用。
 */
@Mapper
public interface EmailDeliveryAdminMapper {
    /**
     * 只读取管理页面需要的字段，不读取正文和租约。
     *
     * @param query 已规范化的状态、创建时间及分页条件
     * @return 按创建时间和任务 ID 逆序的公开字段任务列表，不含正文及租约
     */
    List<EmailDelivery> selectPage(@Param("query") EmailDeliveryAdminQuery query);

    /**
     * 与列表共用状态和时间条件。
     *
     * @param query 邮件投递任务管理员查询条件
     * @return 符合条件的记录数量
     */
    long countPage(@Param("query") EmailDeliveryAdminQuery query);

    /**
     * 查询单条任务的公开字段。
     *
     * @param id 邮件任务主键
     * @return 任务公开字段及原地址；任务不存在时为 null
     */
    EmailDelivery selectDetail(@Param("id") Long id);

    /**
     * 人工重试短事务先锁任务，串行化同一任务上的管理员操作。
     *
     * @param id 待人工重排的邮件任务主键
     * @return 当前任务完整快照，并持有其行锁直到事务结束；不存在时为 null
     */
    EmailDelivery selectLocked(@Param("id") Long id);

    /**
     * 双重约束失败状态和轮次，保留原任务快照及累计次数。
     *
     * @param id 需要重排的原邮件任务主键
     * @param expectedRound 必须匹配的当前 retry_round，成功更新后轮次才加一
     * @param now 重新排队及立即允许工作者领取的当前时间
     * @return FAILED 状态和预期轮次匹配时更新 1 行；条件不匹配时返回 0
     */
    int requeue(@Param("id") Long id, @Param("expectedRound") Integer expectedRound,
                @Param("now") LocalDateTime now);
}
