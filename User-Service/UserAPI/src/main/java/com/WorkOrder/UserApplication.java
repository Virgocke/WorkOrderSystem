package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Virgor
 * @date 2026年09月06日 18:47
 * @description
 */
@SpringBootApplication
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
