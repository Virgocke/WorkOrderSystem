package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.model.EmailDelivery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

/** 邮件任务以条件更新领取；并发实例只有一个能取得当前租约。 */
@Mapper
public interface EmailDeliveryMapper extends BaseMapper<EmailDelivery> {
    /** 选择可发送或租约过期的最早任务，不在 SMTP 调用期间持有数据库锁。 */
    @Select("SELECT * FROM notification_email_deliveries WHERE "
            + "(status IN ('PENDING','RETRY') AND next_attempt_at <= #{now}) "
            + "OR (status = 'SENDING' AND lease_until <= #{now}) ORDER BY id LIMIT 1")
    EmailDelivery selectReady(@Param("now") LocalDateTime now);

    /** 原子领取，返回零表示另一实例已领取该任务。 */
    @Update("UPDATE notification_email_deliveries SET status='SENDING', attempts=attempts+1, "
            + "claim_token=#{token}, lease_until=#{lease}, updated_at=#{now} WHERE id=#{id} AND "
            + "((status IN ('PENDING','RETRY') AND next_attempt_at <= #{now}) "
            + "OR (status='SENDING' AND lease_until <= #{now}))")
    int claim(@Param("id") Long id, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("lease") LocalDateTime lease);

    /** 仅当前租约持有者能完成任务或安排重试。 */
    @Update("UPDATE notification_email_deliveries SET status=#{status}, next_attempt_at=#{next}, "
            + "last_error=#{error}, sent_at=#{sentAt}, claim_token=NULL, lease_until=NULL, updated_at=#{now} "
            + "WHERE id=#{id} AND status='SENDING' AND claim_token=#{token}")
    int finish(@Param("id") Long id, @Param("token") String token, @Param("status") String status,
               @Param("next") LocalDateTime next, @Param("error") String error,
               @Param("sentAt") LocalDateTime sentAt, @Param("now") LocalDateTime now);
}
