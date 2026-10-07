package com.WorkOrder.messaging.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 在环境准备和应用上下文初始化两个阶段，将项目统一配置映射到
 * RocketMQ Spring Starter 的原生配置名。
 * {@code bootstrap.yaml} 可能晚于环境后处理器加载，因此上下文初始化阶段会再次桥接。
 * 显式配置的 rocketmq.* 始终拥有更高优先级。
 */
public class RocketMqPropertyBridgeEnvironmentPostProcessor
        implements EnvironmentPostProcessor,
        ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "workOrderRocketMqPropertyBridge";

    /**
     * 将项目统一消息配置补充为 RocketMQ Starter 能识别的配置键。
     * 已显式配置的 rocketmq.* 属性不会被覆盖。
     *
     * @param environment 当前应用环境
     * @param application 当前 Spring Boot 应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        bridgeProperties(environment);
    }

    /**
     * 在 Spring Cloud Bootstrap 属性已经加入主应用环境后再次执行桥接。
     *
     * @param applicationContext 即将刷新的主应用上下文
     */
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        bridgeProperties(applicationContext.getEnvironment());
    }

    /**
     * 将已启用的统一消息配置复制为 RocketMQ Starter 原生配置。
     *
     * @param environment 当前应用环境
     */
    private void bridgeProperties(ConfigurableEnvironment environment) {
        if (!environment.getProperty("work-order.messaging.enabled", Boolean.class, false)) {
            return;
        }
        Map<String, Object> bridged = new LinkedHashMap<>();
        bridge(environment, bridged, "work-order.messaging.name-server", "rocketmq.name-server");
        bridge(environment, bridged, "work-order.messaging.producer-group", "rocketmq.producer.group");
        bridge(environment, bridged, "work-order.messaging.send-timeout-ms",
                "rocketmq.producer.send-message-timeout");
        if (!bridged.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, bridged));
        }
    }

    /**
     * 在目标属性缺失时复制一个配置值。
     *
     * @param environment 当前应用环境
     * @param bridged 待加入环境的桥接属性
     * @param sourceKey 项目统一配置键
     * @param targetKey RocketMQ Starter 原生配置键
     */
    private void bridge(ConfigurableEnvironment environment, Map<String, Object> bridged,
                        String sourceKey, String targetKey) {
        if (!environment.containsProperty(targetKey) && environment.containsProperty(sourceKey)) {
            bridged.put(targetKey, environment.getProperty(sourceKey));
        }
    }

    /**
     * 使用最低优先级添加桥接属性，确保用户显式配置优先。
     *
     * @return EnvironmentPostProcessor 执行顺序
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
