package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketCancelledPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 根据撤销事件通知相关创建人和处理人。 */
@Component
public class TicketCancelledNotificationHandler {
    /** 站内通知写入入口。 */
    private final NotificationMapper notificationMapper;

    /** 注入站内通知写入入口。 */
    public TicketCancelledNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 使用事件内的接收人快照生成站内通知，写入失败则重试消息。 */
    public void handle(WorkOrderEvent event) {
        TicketCancelledPayload payload = TicketCancelledPayload.from(event);
        String content = "工单 " + payload.getTicketNo() + " 已撤销。";
        LocalDateTime sentAt = LocalDateTime.now();
        for (Long receiverId : payload.getReceiverIds()) {
            Notifications notification = new Notifications();
            notification.setSourceEventId(event.getEventId());
            notification.setTicketId(payload.getTicketId());
            notification.setReceiverId(receiverId);
            notification.setChannel("INTERNAL");
            notification.setContent(content);
            notification.setStatus("SENT");
            notification.setSentAt(sentAt);
            if (notificationMapper.insert(notification) != 1) {
                throw new IllegalStateException("工单撤销站内通知写入失败");
            }
        }
    }
}
