package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Virgor
 * @date 2026/09/06
 * @description 统一鉴权、限流、路由转发、日志记录
 */
@SpringBootApplication
public class GatewayApplication {
    /**
     * 启动服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
