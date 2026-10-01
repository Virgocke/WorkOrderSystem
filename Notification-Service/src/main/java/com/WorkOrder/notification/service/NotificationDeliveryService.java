package com.WorkOrder.notification.service;

import com.WorkOrder.model.notification.NotificationChannels;
import com.WorkOrder.notification.mapper.EmailDeliveryMapper;
import com.WorkOrder.notification.mapper.NotificationChannelMapper;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.EmailDelivery;
import com.WorkOrder.notification.model.Notifications;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.time.LocalDateTime;

/** 所有工单事件共用的渠道分发；通知、邮件任务和消费日志在同一事务提交。 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {
    /** 站内信和邮件通知记录。 */
    private final NotificationMapper notificationMapper;
    /** 实时配置与收件邮箱查询。 */
    private final NotificationChannelMapper channelMapper;
    /** 独立邮件投递队列。 */
    private final EmailDeliveryMapper emailMapper;
    /** 复用项目 JSON 解析器。 */
    private final ObjectMapper objectMapper;

    /** 保存站内信，并按本次消费读取的配置创建邮件任务；禁止脱离消费事务调用。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void deliver(Notifications internal) {
        NotificationChannels channels = currentChannels();
        if (notificationMapper.insert(internal) != 1) {
            throw new IllegalStateException("站内通知写入失败");
        }
        if (!channels.isEmail()) {
            return;
        }
        String recipient = channelMapper.selectEmail(internal.getReceiverId());
        recipient = recipient == null ? null : recipient.trim();
        boolean valid = validRecipient(recipient);
        Notifications email = new Notifications();
        email.setSourceEventId(internal.getSourceEventId());
        email.setTicketId(internal.getTicketId());
        email.setSkillApplicationId(internal.getSkillApplicationId());
        email.setReceiverId(internal.getReceiverId());
        email.setChannel("EMAIL");
        email.setContent(internal.getContent());
        email.setStatus(valid ? "PENDING" : "FAILED");
        if (notificationMapper.insert(email) != 1) {
            throw new IllegalStateException("邮件通知写入失败");
        }
        LocalDateTime now = LocalDateTime.now();
        EmailDelivery task = new EmailDelivery();
        task.setNotificationId(email.getId());
        task.setRecipient(valid ? recipient : null);
        task.setSubject(internal.getSkillApplicationId() == null ? "【智能工单系统】工单通知" : "【智能工单系统】技能申请审核结果");
        task.setContent(email.getContent());
        task.setStatus(valid ? "PENDING" : "FAILED");
        task.setAttempts(0);
        task.setNextAttemptAt(now);
        task.setLastError(valid ? null : "接收人未启用或邮箱缺失、不合法");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (emailMapper.insert(task) != 1) {
            throw new IllegalStateException("邮件任务写入失败");
        }
    }

    /** 仅缺少配置时采用默认值；数据库异常和损坏 JSON 交给消息重试处理。 */
    private NotificationChannels currentChannels() {
        String raw = channelMapper.selectChannels();
        try {
            return NotificationChannels.fromStoredJson(objectMapper.readTree(
                    raw == null ? NotificationChannels.DEFAULT_JSON : raw));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的通知渠道配置不合法", exception);
        }
    }

    /** 只接受单个标准邮箱，不接受地址列表、显示名或换行。 */
    private boolean validRecipient(String recipient) {
        if (recipient == null || recipient.isEmpty() || recipient.length() > 254
                || recipient.contains("\r") || recipient.contains("\n")) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(recipient, true);
            address.validate();
            return recipient.equals(address.getAddress()) && recipient.contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }
}
