package com.WorkOrder.notification.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.ticket.contract.TicketAssignedPayload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 将工单派单事实转换为处理人站内通知。
 */
@Component
public class TicketAssignedNotificationHandler {
    /**
     * 统一通知渠道分发入口。
     */
    private final NotificationDeliveryService deliveryService;

    /**
     * 注入统一通知渠道分发入口。
     *
     * @param deliveryService 在消费事务内同时写入站内信、EMAIL 通知和邮件快照任务的渠道分发服务
     */
    public TicketAssignedNotificationHandler(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * 调用方保证通知与消费日志在同一本地事务中写入。
     *
     * @param event 已通过信封校验的工单派单事件，载荷保存本次动作的业务与接收人快照
     */
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
        deliveryService.deliver(notification);
    }

    /**
     * 组合工单标识与可选派单说明，生成处理人可读的通知。
     *
     * @param payload 派单事件载荷，包含工单标识及可选派单说明
     * @return 供接收处理人阅读的派单通知正文
     */
    private String buildContent(TicketAssignedPayload payload) {
        String ticket = payload.getTicketTitle() == null ? payload.getTicketNo()
                : payload.getTicketNo() + "（" + payload.getTicketTitle() + "）";
        String content = "工单 " + ticket + " 已分配给您，请及时处理。";
        return payload.getReason() == null ? content : content + " 分配说明：" + payload.getReason();
    }
}
