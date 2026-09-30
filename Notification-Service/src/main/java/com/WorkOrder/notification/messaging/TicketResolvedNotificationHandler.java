package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketResolvedPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 通知创建人查看解决结果并确认工单。 */
@Component
public class TicketResolvedNotificationHandler {
    /** 统一通知渠道分发入口。 */
    private final NotificationDeliveryService deliveryService;

    /** 注入统一通知渠道分发入口。 */
    public TicketResolvedNotificationHandler(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
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
            deliveryService.deliver(notification);
        }
    }
}
