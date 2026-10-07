package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketTransferredPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 将工单转派事实转换为新旧处理人的站内通知。
 */
@Component
public class TicketTransferredNotificationHandler {
    /**
     * 统一通知渠道分发入口。
     */
    private final NotificationDeliveryService deliveryService;

    /**
     * 注入统一通知渠道分发入口。
     *
     * @param deliveryService 在消费事务内同时写入站内信、EMAIL 通知和邮件快照任务的渠道分发服务
     */
    public TicketTransferredNotificationHandler(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * 新处理人一定接收；管理员代转时旧处理人也接收。
     *
     * @param event 已通过信封校验的工单转派事件，载荷保存本次动作的业务与接收人快照
     */
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

    /**
     * 为指定新旧处理人写入一条带转派原因的站内通知。
     *
     * @param event 当前转派事件，提供稳定的 sourceEventId
     * @param payload 转派时的工单标识、原处理人、新处理人及原因快照
     * @param receiverId 本条通知对应的新处理人或需通知的原处理人 ID
     * @param action 描述本次转派结果的正文片段，不是可执行回调
     * @param sentAt 本批站内通知生成时刻，不是 SMTP 接受时间
     */
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
