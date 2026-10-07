package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 认证服务启动入口。
 */
@EnableAsync
@SpringBootApplication
public class AuthenticationApplication {
    /**
     * 启动 OAuth2 授权服务器。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthenticationApplication.class, args);
    }
}
