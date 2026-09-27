package com.WorkOrder.messaging.config;

import com.WorkOrder.messaging.consumer.ConsumeLogMapper;
import com.WorkOrder.messaging.consumer.IdempotentConsumerExecutor;
import com.WorkOrder.messaging.metrics.OutboxMetrics;
import com.WorkOrder.messaging.outbox.DomainEventPublisher;
import com.WorkOrder.messaging.outbox.OutboxMapper;
import com.WorkOrder.messaging.outbox.OutboxMessageSender;
import com.WorkOrder.messaging.outbox.OutboxRecoveryJob;
import com.WorkOrder.messaging.outbox.OutboxRelay;
import com.WorkOrder.messaging.outbox.RocketMqOutboxMessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/** 消息底座自动配置；只有 work-order.messaging.enabled=true 时生效。 */
@Configuration
@ConditionalOnClass({DataSource.class, NamedParameterJdbcTemplate.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "work-order.messaging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingProperties.class)
@AutoConfigureAfter(
        value = {DataSourceAutoConfiguration.class, JacksonAutoConfiguration.class},
        name = "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
public class MessagingAutoConfiguration {

    /**
     * 在应用未提供命名参数模板时，基于主数据源创建默认实例。
     *
     * @param dataSource 应用主数据源
     * @return 命名参数 JDBC 模板
     */
    @Bean
    @ConditionalOnMissingBean
    public NamedParameterJdbcTemplate messagingNamedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * 创建 Outbox 数据访问组件。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @return Outbox 数据访问组件
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxMapper outboxMapper(NamedParameterJdbcTemplate jdbcTemplate) {
        return new OutboxMapper(jdbcTemplate);
    }

    /**
     * 创建消费幂等日志数据访问组件。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     * @return 消费日志数据访问组件
     */
    @Bean
    @ConditionalOnMissingBean
    public ConsumeLogMapper consumeLogMapper(NamedParameterJdbcTemplate jdbcTemplate) {
        return new ConsumeLogMapper(jdbcTemplate);
    }

    /**
     * 创建事务性领域事件发布器。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param objectMapper JSON 序列化组件
     * @param properties 消息配置
     * @return 领域事件发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public DomainEventPublisher domainEventPublisher(OutboxMapper outboxMapper,
                                                      ObjectMapper objectMapper,
                                                      MessagingProperties properties) {
        return new DomainEventPublisher(outboxMapper, objectMapper, properties);
    }

    /**
     * 创建消费端幂等事务执行器。
     *
     * @param consumeLogMapper 消费日志数据访问组件
     * @return 幂等消费执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentConsumerExecutor idempotentConsumerExecutor(ConsumeLogMapper consumeLogMapper) {
        return new IdempotentConsumerExecutor(consumeLogMapper);
    }

    @Configuration
    @ConditionalOnClass(RocketMQTemplate.class)
    @ConditionalOnBean(RocketMQTemplate.class)
    static class RocketMqSenderConfiguration {
        /**
         * 在没有自定义发送端口时创建 RocketMQTemplate 适配器。
         *
         * @param rocketMQTemplate RocketMQ Spring 发送模板
         * @param properties 消息配置
         * @return Outbox 消息发送端口
         */
        @Bean
        @ConditionalOnMissingBean(OutboxMessageSender.class)
        OutboxMessageSender rocketMqOutboxMessageSender(RocketMQTemplate rocketMQTemplate,
                                                         MessagingProperties properties) {
            return new RocketMqOutboxMessageSender(rocketMQTemplate, properties);
        }
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "work-order.messaging.outbox", name = "enabled", havingValue = "true")
    static class OutboxSchedulingConfiguration {
        /**
         * 在消息发送端口可用时创建 Outbox 轮询任务。
         *
         * @param outboxMapper Outbox 数据访问组件
         * @param messageSender 消息发送端口
         * @param properties 消息配置
         * @return Outbox Relay
         */
        @Bean
        OutboxRelay outboxRelay(OutboxMapper outboxMapper, OutboxMessageSender messageSender,
                                MessagingProperties properties) {
            return new OutboxRelay(outboxMapper, messageSender, properties);
        }

        /**
         * 创建发送锁超时恢复任务。
         *
         * @param outboxMapper Outbox 数据访问组件
         * @param properties 消息配置
         * @return Outbox 恢复任务
         */
        @Bean
        OutboxRecoveryJob outboxRecoveryJob(OutboxMapper outboxMapper, MessagingProperties properties) {
            return new OutboxRecoveryJob(outboxMapper, properties);
        }
    }

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    static class MetricsConfiguration {
        /**
         * 在 Micrometer 可用时创建 Outbox 指标绑定器。
         *
         * @param outboxMapper Outbox 数据访问组件
         * @param properties 消息配置
         * @return Outbox 指标绑定器
         */
        @Bean
        OutboxMetrics outboxMetrics(OutboxMapper outboxMapper, MessagingProperties properties) {
            return new OutboxMetrics(outboxMapper, properties);
        }
    }
}
