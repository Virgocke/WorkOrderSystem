package com.WorkOrder.messaging.outbox;

/** 隔离具体消息客户端的 Outbox 发送端口。 */
public interface OutboxMessageSender {
    /**
     * 将一条已认领的 Outbox 记录发送到消息中间件。
     *
     * @param event 状态为 SENDING 的 Outbox 记录
     * @return Broker 生成的消息 ID
     */
    String send(OutboxEvent event);
}
