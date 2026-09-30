package com.WorkOrder.notification.job;

import com.WorkOrder.notification.model.EmailDelivery;
import com.WorkOrder.notification.service.EmailDeliveryQueue;
import com.WorkOrder.notification.service.NotificationMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 独立邮件工作者；进程重启后可通过过期租约恢复未完成任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "work-order.notification.email", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryJob {
    /** 领取和回写事务由另一个 Spring Bean 执行。 */
    private final EmailDeliveryQueue queue;
    /** 实际 SMTP 投递入口。 */
    private final NotificationMailSender sender;

    /** 每轮最多处理二十封，默认每十秒执行；失败只记录脱敏任务标识。 */
    @Scheduled(fixedDelayString = "${work-order.notification.email.poll-interval-ms:10000}",
            initialDelayString = "${work-order.notification.email.initial-delay-ms:10000}")
    public void run() {
        for (int i = 0; i < 20; i++) {
            try {
                EmailDelivery task = queue.claim();
                if (task == null) {
                    return;
                }
                boolean success = false;
                String error = "租约恢复次数已达上限";
                if (task.getAttempts() <= EmailDeliveryQueue.MAX_ATTEMPTS) {
                    try {
                        sender.send(task);
                        success = true;
                        error = null;
                    } catch (RuntimeException exception) {
                        error = "邮件发送失败：" + exception.getClass().getSimpleName();
                        log.warn("邮件任务{}第{}次发送失败，异常类型{}", task.getId(), task.getAttempts(), error);
                    }
                }
                queue.finish(task, success, error);
            } catch (RuntimeException exception) {
                // 数据库故障后保留租约，由后续扫描恢复；不把 SMTP 成功回写失败改为普通发送失败。
                log.error("邮件队列处理失败，等待下一轮恢复，异常类型{}", exception.getClass().getSimpleName());
                return;
            }
        }
    }
}
