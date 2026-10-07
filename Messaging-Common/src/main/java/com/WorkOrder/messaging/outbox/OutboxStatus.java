package com.WorkOrder.messaging.outbox;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description Outbox 记录状态。
 */
public enum OutboxStatus {
    /**
     * 新建。
     */
    NEW,
    /**
     * 发送中。
     */
    SENDING,
    /**
     * 已发送。
     */
    SENT,
    /**
     * 重试中。
     */
    RETRY,
    /**
     * 死信。
     */
    DEAD
}
