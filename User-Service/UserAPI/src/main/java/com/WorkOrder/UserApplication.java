package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author Virgor
 * @date 2026年09月06日 18:47
 * @description 用户服务启动入口。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.WorkOrder.user.feignclient")
public class UserApplication {
    /**
     * 启动用户与处理人资料服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
