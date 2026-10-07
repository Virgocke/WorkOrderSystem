package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author Virgor
 * @date 2026年09月06日 18:41
 * @description 智能分配算法，多因子评分，选择最优处理人
 */
@SpringBootApplication
@EnableFeignClients
public class AssignEngineApplication {
    /**
     * 启动服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AssignEngineApplication.class, args);
    }
}
