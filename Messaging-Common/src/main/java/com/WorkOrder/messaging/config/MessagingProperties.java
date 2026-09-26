package com.WorkOrder.messaging.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 消息底座配置。默认关闭，由接入服务显式启用。
 * 标准 getter/setter 由 Spring Boot 配置绑定机制调用。
 */
@ConfigurationProperties(prefix = "work-order.messaging")
public class MessagingProperties {
    /** 是否启用消息底座自动配置。
     * -- SETTER --
     *
     * @param enabled 是否启用消息底座
     */
    @Setter
    private boolean enabled;
    /** RocketMQ 生产组。
     * -- SETTER --
     *
     * @param producerGroup RocketMQ 生产组
     */
    @Setter
    private String producerGroup;
    /** RocketMQ NameServer 地址。
     * -- SETTER --
     *
     * @param nameServer RocketMQ NameServer 地址
     */
    @Setter
    private String nameServer = "localhost:9876";
    /** 同步发送超时时间，单位毫秒。
     * -- SETTER --
     *
     * @param sendTimeoutMs 同步发送超时时间，单位毫秒
     */
    @Setter
    private int sendTimeoutMs = 5000;
    /** 是否按 aggregateId 选择队列进行顺序发送。
     * -- SETTER --
     *
     * @param ordered 是否启用按聚合 ID 的顺序发送
     */
    @Setter
    private boolean ordered = true;
    /** Outbox Relay 配置。 */
    private final Outbox outbox = new Outbox();
    /** 消费端通用配置。 */
    private final Consumer consumer = new Consumer();

    /** @return 是否启用消息底座 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return RocketMQ 生产组 */
    public String getProducerGroup() {
        return producerGroup;
    }

    /** @return RocketMQ NameServer 地址 */
    public String getNameServer() {
        return nameServer;
    }

    /** @return 同步发送超时时间，单位毫秒 */
    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    /** @return 是否启用按聚合 ID 的顺序发送 */
    public boolean isOrdered() {
        return ordered;
    }

    /** @return Outbox 配置 */
    public Outbox getOutbox() {
        return outbox;
    }

    /** @return 消费端配置 */
    public Consumer getConsumer() {
        return consumer;
    }

    /** Outbox Relay、重试和恢复任务配置。 */
    public static class Outbox {
        /** 是否启用 Relay 与恢复任务。 */
        private boolean enabled;
        /** 当前服务的生产者标识和 Outbox 扫描隔离键。 */
        private String sourceService;
        /** Relay 轮询间隔，单位毫秒。 */
        private long pollIntervalMs = 1000L;
        /** 单次最大认领记录数。 */
        private int batchSize = 50;
        /** SENDING 状态锁超时秒数。 */
        private int sendingTimeoutSeconds = 120;
        /** 超时发送记录恢复任务间隔，单位毫秒。 */
        private int recoveryIntervalMs = 30000;
        /** 进入 DEAD 前允许的最大发送失败次数。 */
        private int maxRetries = 12;

        /** @return 是否启用 Outbox Relay 和恢复任务 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用 Outbox Relay 和恢复任务 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return 当前生产服务标识 */
        public String getSourceService() {
            return sourceService;
        }

        /** @param sourceService 当前生产服务标识 */
        public void setSourceService(String sourceService) {
            this.sourceService = sourceService;
        }

        /** @return Relay 轮询间隔，单位毫秒 */
        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        /** @param pollIntervalMs Relay 轮询间隔，单位毫秒 */
        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        /** @return 单次最大认领记录数 */
        public int getBatchSize() {
            return batchSize;
        }

        /** @param batchSize 单次最大认领记录数 */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /** @return SENDING 状态锁超时秒数 */
        public int getSendingTimeoutSeconds() {
            return sendingTimeoutSeconds;
        }

        /** @param sendingTimeoutSeconds SENDING 状态锁超时秒数 */
        public void setSendingTimeoutSeconds(int sendingTimeoutSeconds) {
            this.sendingTimeoutSeconds = sendingTimeoutSeconds;
        }

        /** @return 恢复任务执行间隔，单位毫秒 */
        public int getRecoveryIntervalMs() {
            return recoveryIntervalMs;
        }

        /** @param recoveryIntervalMs 恢复任务执行间隔，单位毫秒 */
        public void setRecoveryIntervalMs(int recoveryIntervalMs) {
            this.recoveryIntervalMs = recoveryIntervalMs;
        }

        /** @return 最大允许发送失败次数 */
        public int getMaxRetries() {
            return maxRetries;
        }

        /** @param maxRetries 最大允许发送失败次数 */
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    /** 消费端通用配置。 */
    public static class Consumer {
        /** Broker 允许的最大重新消费次数。 */
        private int maxReconsumeTimes = 16;

        /** @return Broker 最大重新消费次数 */
        public int getMaxReconsumeTimes() {
            return maxReconsumeTimes;
        }

        /** @param maxReconsumeTimes Broker 最大重新消费次数 */
        public void setMaxReconsumeTimes(int maxReconsumeTimes) {
            this.maxReconsumeTimes = maxReconsumeTimes;
        }
    }
}
