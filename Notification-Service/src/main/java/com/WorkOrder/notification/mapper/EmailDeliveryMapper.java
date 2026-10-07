package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.model.EmailDelivery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 邮件任务以条件更新领取；并发实例只有一个能取得当前租约。
 */
@Mapper
public interface EmailDeliveryMapper extends BaseMapper<EmailDelivery> {
    /**
     * 选择可发送或租约过期的最早任务，不在 SMTP 调用期间持有数据库锁。
     *
     * @param now 判断 next_attempt_at 到期或 SENDING 租约过期的当前时间
     * @return 最早可领取的任务快照；没有符合条件的任务时返回 null
     */
    @Select("SELECT * FROM notification_email_deliveries WHERE "
            + "(status IN ('PENDING','RETRY') AND next_attempt_at <= #{now}) "
            + "OR (status = 'SENDING' AND lease_until <= #{now}) ORDER BY id LIMIT 1")
    EmailDelivery selectReady(@Param("now") LocalDateTime now);

    /**
     * 比较读取到的轮次和次数后领取，拒绝旧快照跨轮或重复领取。
     *
     * @param id 准备领取的邮件任务主键
     * @param expectedRound 读取任务快照时的 retry_round，用于拒绝跨轮次领取
     * @param expectedAttempts 读取任务快照时的本轮领取次数，用于并发比较
     * @param token 此次领取生成的新 claimToken，用于结果回写校验
     * @param now 此次领取的当前时间，同时用于判断任务是否到期
     * @param lease 此次领取后的租约到期时刻
     * @return 成功领取返回 1；状态、轮次或次数已变化时返回 0
     */
    @Update("UPDATE notification_email_deliveries SET status='SENDING', attempts=attempts+1, "
            + "total_attempts=total_attempts+1, "
            + "claim_token=#{token}, lease_until=#{lease}, updated_at=#{now} WHERE id=#{id} AND "
            + "retry_round=#{expectedRound} AND attempts=#{expectedAttempts} AND "
            + "((status IN ('PENDING','RETRY') AND next_attempt_at <= #{now}) "
            + "OR (status='SENDING' AND lease_until <= #{now}))")
    int claim(@Param("id") Long id, @Param("expectedRound") Integer expectedRound,
              @Param("expectedAttempts") Integer expectedAttempts, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("lease") LocalDateTime lease);

    /**
     * 仅当前租约持有者能完成任务或安排重试。
     *
     * @param id 需要回写结果的邮件任务主键
     * @param token 本次领取持有的 claimToken，旧令牌不能回写
     * @param round 本次领取所在的人工重发轮次
     * @param status 按发送结果确定的 SENT、RETRY 或 FAILED 状态
     * @param next 失败退避后最早允许重新领取的时刻
     * @param error 脱敏失败摘要，成功时为空
     * @param sentAt SMTP 接受时间，发送失败时为空
     * @param now 此次结果回写时间
     * @return 当前 SENDING 任务的令牌和轮次匹配时返回 1；旧工作者或状态已变化时返回 0
     */
    @Update("UPDATE notification_email_deliveries SET status=#{status}, next_attempt_at=#{next}, "
            + "last_error=#{error}, sent_at=#{sentAt}, claim_token=NULL, lease_until=NULL, updated_at=#{now} "
            + "WHERE id=#{id} AND status='SENDING' AND claim_token=#{token} AND retry_round=#{round}")
    int finish(@Param("id") Long id, @Param("token") String token, @Param("round") Integer round,
               @Param("status") String status,
               @Param("next") LocalDateTime next, @Param("error") String error,
               @Param("sentAt") LocalDateTime sentAt, @Param("now") LocalDateTime now);
}
