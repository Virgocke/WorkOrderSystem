package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketRemindedPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将催办事件中的接收人快照转换为站内通知。 */
@Component
public class TicketRemindedNotificationHandler {
    /** 站内通知持久化入口。 */
    private final NotificationMapper notificationMapper;

    /** 注入站内通知持久化入口。 */
    public TicketRemindedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 调用方保证全部通知与消费日志在同一本地事务中写入。 */
    public void handle(WorkOrderEvent event) {
        TicketRemindedPayload payload = TicketRemindedPayload.from(event);
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        String content = "用户第 " + payload.getRemindCount() + " 次催办工单 "
                + ticket + "，请及时关注处理进度。";
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
                throw new IllegalStateException("工单催办站内通知写入失败");
            }
        }
    }
}
