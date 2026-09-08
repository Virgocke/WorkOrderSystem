package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Virgor
 * @date 2026年09月06日 18:46
 * @description 工单 CRUD、状态机管理、SLA 时限计算、工单查询
 */
@SpringBootApplication
public class TicketApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
