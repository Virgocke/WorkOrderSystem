package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketResolvedPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 通知创建人查看解决结果并确认工单。 */
@Component
public class TicketResolvedNotificationHandler {
    /** 站内通知持久化入口。 */
    private final NotificationMapper notificationMapper;

    /** 注入站内通知持久化入口。 */
    public TicketResolvedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 按事件内的创建人快照写入“查看并确认”通知；失败时交由消息重试。 */
    public void handle(WorkOrderEvent event) {
        TicketResolvedPayload payload = TicketResolvedPayload.from(event);
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        String content = "工单 " + ticket + " 已解决，请查看解决方案并确认。";
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
                throw new IllegalStateException("工单解决站内通知写入失败");
            }
        }
    }
}
