package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Virgor
 * @date 2026年09月06日 18:45
 * @description 定时扫描超时工单，触发告警升级；SLA 报表统计
 */
@SpringBootApplication
public class SlaMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SlaMonitorApplication.class, args);
    }
}
