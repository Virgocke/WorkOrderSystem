package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketRepliedPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将回复事件中的接收人快照转换为站内通知。 */
@Component
public class TicketRepliedNotificationHandler {
    /** 统一通知渠道分发入口。 */
    private final NotificationDeliveryService deliveryService;

    /** 注入统一通知渠道分发入口。 */
    public TicketRepliedNotificationHandler(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** 调用方保证全部通知与消费日志在同一本地事务中写入。 */
    public void handle(WorkOrderEvent event) {
        TicketRepliedPayload payload = TicketRepliedPayload.from(event);
        String content = buildContent(payload);
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

    /** 按回复类型生成给处理人或创建人的不同提示。 */
    private String buildContent(TicketRepliedPayload payload) {
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        return "USER_REPLY".equals(payload.getReplyType())
                ? "用户已回复工单 " + ticket + "，请及时处理。"
                : "工单 " + ticket + " 有新的处理回复，请及时查看。";
    }
}
