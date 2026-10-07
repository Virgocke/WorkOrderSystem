package com.WorkOrder.notification.messaging;

import com.WorkOrder.ticket.contract.TicketEscalatedPayload;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.notification.service.NotificationDeliveryService;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.notification.mapper.AlertRecordMapper;
import com.WorkOrder.notification.model.AlertRecords;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 将工单升级事件转换为接收人站内通知，并为自动升级创建告警记录。
 */
@Component
public class TicketEscalatedNotificationHandler {
    /**
     * 统一通知渠道分发入口。
     */
    private final NotificationDeliveryService deliveryService;
    /**
     * 自动升级告警记录，与站内通知同事务写入。
     */
    private final AlertRecordMapper alertRecordMapper;

    /**
     * 创建升级事件通知处理器。
     *
     * @param deliveryService 统一通知渠道分发入口
     * @param alertRecordMapper 自动升级告警记录，与站内通知同事务写入
     */
    public TicketEscalatedNotificationHandler(NotificationDeliveryService deliveryService, AlertRecordMapper alertRecordMapper) {
        this.deliveryService = deliveryService;
        this.alertRecordMapper = alertRecordMapper;
    }

    /**
     * 按事件中的接收人和工单摘要快照写入站内通知、自动升级告警；与消费日志共用本地事务。
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
            deliveryService.deliver(notification);
            if (payload.isAutomatic()) {
                AlertRecords alert = new AlertRecords();
                alert.setTicketId(payload.getTicketId());
                // 保存事件中的工单摘要，历史告警不再依赖工单当前的标题和访问权限。
                alert.setTicketNoSnapshot(payload.getTicketNo());
                alert.setTicketTitleSnapshot(payload.getTicketTitle());
                alert.setAlertType("ESCALATION");
                alert.setLevel(payload.getEscalationLevel());
                // 告警列限长500；完整内容保留于通知和升级操作日志。
                alert.setMessage(content.length() > 500 ? content.substring(0, 500) : content);
                alert.setTargetUserId(receiverId);
                alert.setNotificationChannel("INTERNAL");
                alert.setStatus("SENT");
                alert.setSentAt(sentAt);
                alert.setCreatedAt(sentAt);
                if (alertRecordMapper.insert(alert) != 1) {
                    throw new IllegalStateException("自动升级告警写入失败");
                }
            }
        }
    }
}
