package com.WorkOrder.messaging.metrics;

import com.WorkOrder.messaging.config.MessagingProperties;
import com.WorkOrder.messaging.outbox.OutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;


/**
 * @author Virgor
 * @date 2026年10月07日
 * @description Outbox 基础积压、死信和超时指标。
 */
@Slf4j
public class OutboxMetrics implements MeterBinder {
    private final OutboxMapper outboxMapper;
    private final MessagingProperties properties;

    /**
     * 创建 Outbox 指标绑定器。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param properties 消息配置
     */
    public OutboxMetrics(OutboxMapper outboxMapper, MessagingProperties properties) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
    }

    /**
     * 向 Micrometer 注册积压、积压时长、超时发送和死信指标。
     *
     * @param registry Micrometer 指标注册表
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        String sourceService = properties.getOutbox().getSourceService();
        // 待发送消息数量
        Gauge.builder("work.order.outbox.pending.total", this, OutboxMetrics::pending)
                .tag("source_service", sourceService).register(registry);
        // 最老待发送消息的积压秒数
        Gauge.builder("work.order.outbox.oldest.pending.seconds", this, OutboxMetrics::oldestPendingSeconds)
                .tag("source_service", sourceService).register(registry);
        // 发送锁超时记录数量
        Gauge.builder("work.order.outbox.sending.stale.total", this, OutboxMetrics::staleSending)
                .tag("source_service", sourceService).register(registry);
        // 死信消息数量
        Gauge.builder("work.order.outbox.dead.total", this, OutboxMetrics::dead)
                .tag("source_service", sourceService).register(registry);
    }

    /**
     * 读取当前生产服务的待发送记录数量。
     *
     * @return 当前服务 NEW 或 RETRY 记录数；数据库查询失败时为 NaN
     */
    private double pending() {
        return safely(() -> outboxMapper.countPending(properties.getOutbox().getSourceService()));
    }

    /**
     * 读取当前生产服务的死信记录数量。
     *
     * @return 当前服务达到失败上限的 DEAD 记录数；数据库查询失败时为 NaN
     */
    private double dead() {
        return safely(() -> outboxMapper.countDead(properties.getOutbox().getSourceService()));
    }

    /**
     * 读取当前生产服务需要恢复的超时发送记录数量。
     *
     * @return 当前服务超过发送锁时限的 SENDING 记录数；数据库查询失败时为 NaN
     */
    private double staleSending() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusSeconds(properties.getOutbox().getSendingTimeoutSeconds());
        return safely(() -> outboxMapper.countStaleSending(
                properties.getOutbox().getSourceService(), threshold));
    }

    /**
     * 计算当前生产服务最老待发送记录的积压秒数。
     *
     * @return 最老 NEW 或 RETRY 记录至今的积压秒数；无记录时为 0，数据库查询失败时为 NaN
     */
    private double oldestPendingSeconds() {
        try {
            LocalDateTime oldest = outboxMapper.findOldestPendingCreatedAt(
                    properties.getOutbox().getSourceService());
            return oldest == null ? 0D : Math.max(0L, Duration.between(oldest, LocalDateTime.now()).getSeconds());
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }

    /**
     * 安全执行指标查询，避免数据库短暂异常影响指标采集线程。
     *
     * @param supplier 指标数值提供器
     * @return 查询值；查询异常时返回 NaN
     */
    private double safely(LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException exception) {
            log.warn("获取指标失败", exception);
            return Double.NaN;
        }
    }

    /**
     * @author Virgor
     * @date 2026年10月07日
     * @description LongSupplier契约。
     */
    private interface LongSupplier {
        /**
         * 获取AsLong。
         *
         * @return 当前指标的 long 数值
         */
        long getAsLong();
    }
}
