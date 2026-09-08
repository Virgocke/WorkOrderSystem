package com.WorkOrder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Virgor
 * @date 2026年09月06日 18:44
 * @description 工单全文检索、历史数据分析（基于 ES）
 */
@SpringBootApplication
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
