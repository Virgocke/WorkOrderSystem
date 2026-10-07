package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.model.EmailDeliveryRetryLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 已成功重排的审计和请求幂等结果。
 */
@Mapper
public interface EmailDeliveryRetryLogMapper extends BaseMapper<EmailDeliveryRetryLog> {
    /**
     * 在任务行锁之后查询，避免两个管理员创建相同轮次。
     *
     * @param deliveryId 原邮件任务主键
     * @param requestId 规范化为小写的 32 位十六进制请求幂等键
     * @return 此前成功提交的同请求审计；尚未接受该请求时为 null
     */
    @Select("SELECT * FROM notification_email_retry_logs WHERE delivery_id=#{deliveryId} AND request_id=#{requestId}")
    EmailDeliveryRetryLog selectByRequest(@Param("deliveryId") Long deliveryId,
                                         @Param("requestId") String requestId);

    /**
     * 按接受轮次逆序稳定分页，多个操作同秒发生也不乱序。
     *
     * @param deliveryId 原邮件任务主键
     * @param pageSize 本页最大日志条数，已限制为 1 至 100
     * @param offset 按页码计算的跳过条数，从 0 开始
     * @return 按 to_round、id 逆序的人工重发审计列表
     */
    @Select("SELECT * FROM notification_email_retry_logs WHERE delivery_id=#{deliveryId} "
            + "ORDER BY to_round DESC, id DESC LIMIT #{pageSize} OFFSET #{offset}")
    List<EmailDeliveryRetryLog> selectRetryLogs(@Param("deliveryId") Long deliveryId,
                                          @Param("pageSize") Long pageSize, @Param("offset") long offset);

    /**
     * 重发日志总数，与分页列表在同一只读快照中读取。
     *
     * @param deliveryId 邮件投递任务 ID
     * @return 符合条件的记录数量
     */
    @Select("SELECT COUNT(*) FROM notification_email_retry_logs WHERE delivery_id=#{deliveryId}")
    long countByDelivery(@Param("deliveryId") Long deliveryId);
}
