package com.WorkOrder.messaging.outbox;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 隔离具体消息客户端的 Outbox 发送端口。
 */
public interface OutboxMessageSender {
    /**
     * 将一条已认领的 Outbox 记录发送到消息中间件。
     *
     * @param event 已认领且状态为 SENDING 的 Outbox 记录
     * @return 消息中间件接受本次发送后返回的消息 ID
     */
    String send(OutboxEvent event);
}
