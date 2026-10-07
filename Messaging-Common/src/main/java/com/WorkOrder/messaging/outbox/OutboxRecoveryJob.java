package com.WorkOrder.messaging.outbox;

import com.WorkOrder.messaging.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 将超过发送锁时限的 SENDING 记录恢复为 RETRY。
 */
public class OutboxRecoveryJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRecoveryJob.class);

    private final OutboxMapper outboxMapper;
    private final MessagingProperties properties;

    /**
     * 创建发送锁恢复任务。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param properties 消息配置
     */
    public OutboxRecoveryJob(OutboxMapper outboxMapper, MessagingProperties properties) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
    }

    /**
     * 周期性恢复超过 sending-timeout 的 SENDING 记录，使其可再次投递。
     */
    @Scheduled(fixedDelayString = "${work-order.messaging.outbox.recovery-interval-ms:30000}")
    public void recover() {
        LocalDateTime lockedBefore = LocalDateTime.now()
                .minusSeconds(properties.getOutbox().getSendingTimeoutSeconds());
        int recovered = outboxMapper.recoverStale(
                properties.getOutbox().getSourceService(), lockedBefore);
        if (recovered > 0) {
            LOGGER.warn("已恢复 {} 条发送锁超时的 Outbox 记录，sourceService={}",
                    recovered, properties.getOutbox().getSourceService());
        }
    }
}
