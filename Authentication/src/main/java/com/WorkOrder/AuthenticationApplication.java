package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 认证服务启动入口。 */
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
