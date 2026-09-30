package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketTransferredPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将工单转派事实转换为新旧处理人的站内通知。 */
@Component
public class TicketTransferredNotificationHandler {
    /** 统一通知渠道分发入口。 */
    private final NotificationDeliveryService deliveryService;

    /** 注入统一通知渠道分发入口。 */
    public TicketTransferredNotificationHandler(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** 新处理人一定接收；管理员代转时旧处理人也接收。 */
    public void handle(WorkOrderEvent event) {
        TicketTransferredPayload payload = TicketTransferredPayload.from(event);
        LocalDateTime sentAt = LocalDateTime.now();
        insert(event, payload, payload.getToHandlerId(),
                "已转派给您，请及时处理。转派原因：", sentAt);
        if (payload.getTransferredBy() != payload.getFromHandlerId()) {
            insert(event, payload, payload.getFromHandlerId(),
                    "已由管理员转派给其他处理人。转派原因：", sentAt);
        }
    }

    /** 为指定新旧处理人写入一条带转派原因的站内通知。 */
    private void insert(WorkOrderEvent event, TicketTransferredPayload payload,
                        long receiverId, String action, LocalDateTime sentAt) {
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        Notifications notification = new Notifications();
        notification.setSourceEventId(event.getEventId());
        notification.setTicketId(payload.getTicketId());
        notification.setReceiverId(receiverId);
        notification.setChannel("INTERNAL");
        notification.setContent("工单 " + ticket + " " + action + payload.getReason());
        notification.setStatus("SENT");
        notification.setSentAt(sentAt);
        deliveryService.deliver(notification);
    }
}
