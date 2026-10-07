package com.WorkOrder.messaging.outbox;

import com.WorkOrder.messaging.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 周期性认领并投递 Outbox 消息。数据库认领事务在网络发送前已提交。
 */
public class OutboxRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMapper outboxMapper;
    private final OutboxMessageSender messageSender;
    private final MessagingProperties properties;
    private final String instanceId;

    /**
     * 创建 Outbox Relay，并为当前进程生成唯一实例标识。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param messageSender 消息发送端口
     * @param properties 消息配置
     */
    public OutboxRelay(OutboxMapper outboxMapper, OutboxMessageSender messageSender,
                       MessagingProperties properties) {
        this.outboxMapper = outboxMapper;
        this.messageSender = messageSender;
        this.properties = properties;
        this.instanceId = createInstanceId(properties.getOutbox().getSourceService());
    }

    /**
     * 周期性认领并逐条发送一批到期消息。
     * 认领事务由 OutboxMapper 在本方法调用发送端口前完成提交。
     */
    @Scheduled(fixedDelayString = "${work-order.messaging.outbox.poll-interval-ms:1000}")
    public void relay() {
        List<OutboxEvent> events = outboxMapper.claimAvailable(
                properties.getOutbox().getSourceService(),
                instanceId,
                properties.getOutbox().getBatchSize());
        for (OutboxEvent event : events) {
            sendOne(event);
        }
    }

    /**
     * 同步发送单条消息，并根据结果推进 Outbox 状态。
     *
     * @param event 已由当前实例认领的 SENDING 记录
     */
    private void sendOne(OutboxEvent event) {
        try {
            String messageId = messageSender.send(event);
            int updated = outboxMapper.markSent(event.getId(), instanceId, messageId);
            if (updated == 0) {
                LOGGER.warn("Outbox 成功发送但状态更新未生效，eventId={}, messageId={}",
                        event.getEventId(), messageId);
            } else {
                LOGGER.info("Outbox 发送成功，eventId={}, eventType={}, topic={}, tag={}, messageId={}",
                        event.getEventId(), event.getEventType(), event.getTopic(), event.getTag(), messageId);
            }
        } catch (Exception exception) {
            String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            outboxMapper.markFailed(event, instanceId, properties.getOutbox().getMaxRetries(), error);
            LOGGER.error("Outbox 发送失败，eventId={}, eventType={}, retryCount={}",
                    event.getEventId(), event.getEventType(), event.getRetryCount() + 1, exception);
        }
    }

    /**
     * 返回当前 Relay 的实例标识，供状态更新和测试校验使用。
     *
     * @return 当前实例标识
     */
    String getInstanceId() {
        return instanceId;
    }

    /**
     * 使用服务名、主机名和随机后缀生成实例标识。
     *
     * @param sourceService 生产服务标识
     * @return Relay 实例标识
     */
    private String createInstanceId(String sourceService) {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            LOGGER.warn("无法解析当前主机名，将使用占位值生成 Relay 实例 ID");
        }
        return sourceService + "@" + host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
