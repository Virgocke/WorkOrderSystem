package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketAssignedPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将工单派单事实转换为处理人站内通知。 */
@Component
public class TicketAssignedNotificationHandler {
    /** 站内通知持久化入口。 */
    private final NotificationMapper notificationMapper;

    /** 注入站内通知持久化入口。 */
    public TicketAssignedNotificationHandler(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 调用方保证通知与消费日志在同一本地事务中写入。 */
    public void handle(WorkOrderEvent event) {
        TicketAssignedPayload payload = TicketAssignedPayload.from(event);
        Notifications notification = new Notifications();
        notification.setSourceEventId(event.getEventId());
        notification.setTicketId(payload.getTicketId());
        notification.setReceiverId(payload.getHandlerId());
        notification.setChannel("INTERNAL");
        notification.setContent(buildContent(payload));
        notification.setStatus("SENT");
        notification.setSentAt(LocalDateTime.now());
        if (notificationMapper.insert(notification) != 1) {
            throw new IllegalStateException("工单派单站内通知写入失败");
        }
    }

    /** 组合工单标识与可选派单说明，生成处理人可读的通知。 */
    private String buildContent(TicketAssignedPayload payload) {
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        String content = "工单 " + ticket + " 已分配给您，请及时处理。";
        return payload.getReason() == null ? content : content + " 分配说明：" + payload.getReason();
    }
}
