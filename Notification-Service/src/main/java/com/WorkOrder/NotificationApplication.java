package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Virgor
 * @date 2026年09月06日 18:44
 * @description 发送站内信、邮件、短信等通知，处理 RocketMQ 消息
 */
@SpringBootApplication
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
