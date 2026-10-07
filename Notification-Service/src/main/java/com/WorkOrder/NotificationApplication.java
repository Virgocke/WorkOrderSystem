package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author Virgor
 * @date 2026年09月06日 18:44
 * @description 发送站内信、邮件通知，处理 RocketMQ 消息
 */
@SpringBootApplication
@EnableFeignClients
@org.springframework.scheduling.annotation.EnableScheduling
public class NotificationApplication {
    /**
     * 启动服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
