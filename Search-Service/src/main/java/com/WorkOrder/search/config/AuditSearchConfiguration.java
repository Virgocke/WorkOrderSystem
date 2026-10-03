package com.WorkOrder.search.config;

import com.WorkOrder.search.repository.AuditSearchRepository;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 复用现有 ES 客户端，仅在功能开启时注册审计仓库及周期同步调度。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuditSearchProperties.class)
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class AuditSearchConfiguration {
    /** 注册审计仓库；首次周期同步负责创建索引和回填，构造不连接 ES。 */
    @Bean
    public AuditSearchRepository auditSearchRepository(RestHighLevelClient client,
                                                       ElasticsearchProperties elasticsearch,
                                                       AuditSearchProperties audit) {
        return new AuditSearchRepository(client, elasticsearch, audit);
    }
}
