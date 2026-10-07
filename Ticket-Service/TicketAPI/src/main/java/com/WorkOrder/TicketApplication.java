package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author Virgor
 * @date 2026年09月06日 18:46
 * @description 工单 CRUD、状态机管理、SLA 时限计算、工单查询
 */
@SpringBootApplication
@EnableFeignClients
public class TicketApplication {
    /**
     * 启动服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
