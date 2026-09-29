package com.WorkOrder.notification.messaging;

import com.WorkOrder.ticket.contract.TicketEscalatedPayload;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将工单升级事件转换为管理员站内通知。 */
@Component
public class TicketEscalatedNotificationHandler {
    /** 站内通知持久化入口。 */
    private final NotificationMapper notificationMapper;

    /** 创建升级事件通知处理器。 */
    public TicketEscalatedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 按事件中的管理员快照写入站内通知；调用方将通知和消费日志置于同一本地事务。
     *
     * @param event 已通过统一信封校验的升级事件
     */
    public void handle(WorkOrderEvent event) {
        TicketEscalatedPayload payload = TicketEscalatedPayload.from(event);
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        String content = "工单 " + ticket + " 已升级至 " + payload.getEscalationLevel()
                + " 级。升级原因：" + payload.getReason();
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
                throw new IllegalStateException("工单升级站内通知写入失败");
            }
        }
    }
}
