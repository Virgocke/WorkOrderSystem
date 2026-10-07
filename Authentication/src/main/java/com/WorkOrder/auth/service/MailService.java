package com.WorkOrder.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * @author Virgor
 * @date 2026年09月12日 00:26
 * @description 邮箱服务
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 向指定邮箱发送验证码邮件。
     *
     * @param to 验证码邮件的收件邮箱
     * @param code 待发送的邮箱验证码
     */
    public void sendVerificationCode(String to, String code){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("【智能工单系统】邮箱验证码");
        message.setText("您的验证码是：" + code + "，5分钟内有效。请勿泄露给他人。");
        mailSender.send(message);
    }
}
