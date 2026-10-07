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
import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 所有工单事件共用的渠道分发；通知、邮件任务和消费日志在同一事务提交。
 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {
    /**
     * 站内信和邮件通知记录。
     */
    private final NotificationMapper notificationMapper;
    /**
     * 实时配置与收件邮箱查询。
     */
    private final NotificationChannelMapper channelMapper;
    /**
     * 独立邮件投递队列。
     */
    private final EmailDeliveryMapper emailMapper;
    /**
     * 复用项目 JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 在调用方消费事务中保存站内信，并按当前渠道配置生成 EMAIL 通知及邮件快照任务。
     *
     * @param internal 尚未持久化的 INTERNAL 通知，包含事件 ID、原接收人及通知内容
     */
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
        boolean valid = EmailRecipientValidator.isValid(recipient);
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
        task.setRetryRound(0);
        task.setTotalAttempts(0L);
        task.setNextAttemptAt(now);
        task.setLastError(valid ? null : "接收人未启用或邮箱缺失、不合法");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (emailMapper.insert(task) != 1) {
            throw new IllegalStateException("邮件任务写入失败");
        }
    }

    /**
     * 仅缺少配置时采用默认值；数据库异常和损坏 JSON 交给消息重试处理。
     *
     * @return 本次消费读取并校验后的渠道配置；仅数据库无配置记录时采用默认值
     */
    private NotificationChannels currentChannels() {
        String raw = channelMapper.selectChannels();
        try {
            return NotificationChannels.fromStoredJson(objectMapper.readTree(
                    raw == null ? NotificationChannels.DEFAULT_JSON : raw));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的通知渠道配置不合法", exception);
        }
    }

}
