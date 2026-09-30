package com.WorkOrder.notification.service;

import com.WorkOrder.notification.mapper.EmailDeliveryMapper;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.EmailDelivery;
import com.WorkOrder.notification.model.Notifications;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

/** 邮件任务领取和状态回写各自使用短事务，与外部 SMTP 调用分离。 */
@Service
@RequiredArgsConstructor
public class EmailDeliveryQueue {
    /** 每条任务最多执行五次 SMTP 尝试。 */
    public static final int MAX_ATTEMPTS = 5;
    /** 邮件任务持久化入口。 */
    private final EmailDeliveryMapper emailMapper;
    /** 邮件投递结果同步到通知记录。 */
    private final NotificationMapper notificationMapper;

    /** 原子取得五分钟租约；竞争失败时本轮返回空，下一轮继续。 */
    @Transactional(rollbackFor = Exception.class)
    public EmailDelivery claim() {
        LocalDateTime now = LocalDateTime.now();
        EmailDelivery task = emailMapper.selectReady(now);
        if (task == null) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        if (emailMapper.claim(task.getId(), token, now, now.plusMinutes(5)) != 1) {
            return null;
        }
        task.setClaimToken(token);
        task.setAttempts(task.getAttempts() + 1);
        task.setStatus("SENDING");
        return task;
    }

    /** 成功标记 SENT；失败指数退避，达上限后 FAILED；过期令牌无权更新结果。 */
    @Transactional(rollbackFor = Exception.class)
    public void finish(EmailDelivery task, boolean success, String error) {
        LocalDateTime now = LocalDateTime.now();
        String status = success ? "SENT" : task.getAttempts() >= MAX_ATTEMPTS ? "FAILED" : "RETRY";
        LocalDateTime next = now.plusSeconds(30L << Math.min(task.getAttempts() - 1, 6));
        if (emailMapper.finish(task.getId(), task.getClaimToken(), status, next, error,
                success ? now : null, now) != 1) {
            return;
        }
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notifications>()
                .eq(Notifications::getId, task.getNotificationId())
                .eq(Notifications::getChannel, "EMAIL")
                .set(Notifications::getStatus, "RETRY".equals(status) ? "PENDING" : status)
                .set(Notifications::getSentAt, success ? now : null));
        if (updated != 1) {
            throw new IllegalStateException("邮件通知状态更新失败");
        }
    }
}
