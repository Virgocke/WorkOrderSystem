package com.WorkOrder.notification.service;

import com.WorkOrder.notification.model.EmailDelivery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** 使用通知服务自己的 SMTP 配置发送工单纯文本邮件，与认证验证码解耦。 */
@Service
public class NotificationMailSender {
    /** 未配置 spring.mail.host 时为空，任务会记录失败并按策略重试。 */
    private final ObjectProvider<JavaMailSender> senderProvider;
    /** 发件地址通过外部配置提供，默认使用 SMTP 账号。 */
    private final String from;

    /** 注入可选 SMTP 组件及发件地址；不要求本地开发必须配置邮件服务器。 */
    public NotificationMailSender(ObjectProvider<JavaMailSender> senderProvider,
            @Value("${work-order.notification.email.from:${spring.mail.username:}}") String from) {
        this.senderProvider = senderProvider;
        this.from = from;
    }

    /** 只在数据库事务外调用；SMTP 接受邮件后返回，不表示已到达收件箱。 */
    public void send(EmailDelivery task) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null || from == null || from.trim().isEmpty()) {
            throw new IllegalStateException("通知服务尚未配置SMTP或发件地址");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(task.getRecipient());
        message.setSubject(task.getSubject());
        message.setText(task.getContent());
        sender.send(message);
    }
}
