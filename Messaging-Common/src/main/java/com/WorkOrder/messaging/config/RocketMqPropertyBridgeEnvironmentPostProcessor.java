package com.WorkOrder.messaging.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将项目统一配置映射到 RocketMQ Spring Starter 的原生配置名。
 * 显式配置的 rocketmq.* 始终拥有更高优先级。
 */
public class RocketMqPropertyBridgeEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {
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
