package com.WorkOrder.messaging.outbox;

import com.WorkOrder.messaging.config.MessagingProperties;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/** RocketMQTemplate 发送适配器。 */
public class RocketMqOutboxMessageSender implements OutboxMessageSender {
    private final RocketMQTemplate rocketMQTemplate;
    private final MessagingProperties properties;

    /**
     * 创建 RocketMQ 发送适配器。
     *
     * @param rocketMQTemplate RocketMQ Spring 发送模板
     * @param properties 消息配置
     */
    public RocketMqOutboxMessageSender(RocketMQTemplate rocketMQTemplate,
                                       MessagingProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    /**
     * 使用 eventId 作为消息 Key 发送完整事件 JSON。
     * 开启有序发送时使用 aggregateId 作为队列选择键。
     *
     * @param event 待发送的 Outbox 记录
     * @return Broker 消息 ID
     */
    @Override
    public String send(OutboxEvent event) {
        String destination = event.getTopic() + ":" + event.getTag();
        // 创建消息
        Message<String> message = MessageBuilder
                .withPayload(event.getPayload())
                .setHeader(RocketMQHeaders.KEYS, event.getMessageKey())
                .build();
        SendResult result;
        // 发送消息
        if (properties.isOrdered()) {
            result = rocketMQTemplate.syncSendOrderly(
                            destination,
                            message,
                            event.getAggregateId(),
                            properties.getSendTimeoutMs());
        } else {
            result = rocketMQTemplate.syncSend(destination, message, properties.getSendTimeoutMs());
        }
        if (result == null || result.getMsgId() == null) {
            throw new IllegalStateException("RocketMQ 未返回消息 ID");
        }
        return result.getMsgId();
    }
}
