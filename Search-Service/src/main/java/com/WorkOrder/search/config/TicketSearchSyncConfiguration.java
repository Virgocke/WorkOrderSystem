package com.WorkOrder.search.config;

import com.WorkOrder.messaging.config.MessagingProperties;
import com.WorkOrder.search.mapper.TicketIndexSourceMapper;
import com.WorkOrder.search.mapper.TicketSearchWriteTargetMapper;
import com.WorkOrder.search.messaging.TicketSearchChangedListener;
import com.WorkOrder.search.repository.TicketSearchRepository;
import com.WorkOrder.search.service.TicketProjectionService;
import com.WorkOrder.search.service.TicketSearchDocumentConverter;
import com.WorkOrder.search.service.TicketSearchIndexService;
import com.WorkOrder.search.service.TicketSearchWriteTargetResolver;
import org.apache.rocketmq.spring.autoconfigure.ListenerContainerConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 独立校验同步开关，即使 ES 配置被禁用也能报告配置矛盾。
 */
@Configuration
@EnableConfigurationProperties({TicketSearchSyncProperties.class,
        ElasticsearchProperties.class, MessagingProperties.class})
public class TicketSearchSyncConfiguration {
    private static final Pattern MQ_NAME = Pattern.compile("[%|a-zA-Z0-9_-]+");

    /**
     * 校验配置而不访问远程服务；初始 NOT_READY 不妨碍管理服务启动。
     *
     * @param sync 同步
     * @param elasticsearch Elasticsearch配置属性，对应 elasticsearch
     * @param messaging 消息
     * @param environment Environment，对应 environment
     * @return 操作是否成功
     */
    @Bean
    public Boolean ticketSearchSyncRequirements(TicketSearchSyncProperties sync,
                                               ElasticsearchProperties elasticsearch,
                                               MessagingProperties messaging,
                                               Environment environment) {
        if (!sync.isEnabled()) {
            // 未开启同步时不强制依赖 ES/MQ，管理入口仍可正常启动并返回未就绪。
            return Boolean.TRUE;
        }
        if (!elasticsearch.isEnabled()) {
            throw new IllegalStateException("ticket-sync.enabled=true 要求 work-order.elasticsearch.enabled=true");
        }
        // 批次、执行预算和租约一起限制扫描负载，启动时拒绝会导致持续失败的配置。
        if (sync.getBatchSize() <= 0 || sync.getBatchSize() > elasticsearch.getMaxBulkSize()
                || sync.getBatchesPerRun() <= 0 || sync.getBatchesPerRun() > 100
                || sync.getImportLeaseSeconds() < 30 || sync.getImportLeaseSeconds() > 3600
                || sync.getImportDelayMs() < 100 || sync.getReconcileDelayMs() < 1000) {
            throw new IllegalStateException("工单搜索批次、调度间隔或租约配置无效");
        }
        if (!messaging.isEnabled()) {
            throw new IllegalStateException("ticket-sync.enabled=true 要求 work-order.messaging.enabled=true");
        }
        String nameServer = environment.getProperty("rocketmq.name-server", messaging.getNameServer());
        if (!StringUtils.hasText(nameServer)) {
            throw new IllegalStateException("工单搜索同步必须配置 RocketMQ NameServer");
        }
        validateMqName(sync.getConsumerGroup(), 255, "consumer-group");
        validateMqName(environment.getProperty("work-order.messaging.ticket-search-topic",
                "wo-ticket-search-event"), 127, "ticket-search-topic");
        try {
            if (!StringUtils.hasText(sync.getSourceZoneId())) {
                throw new DateTimeException("empty zone");
            }
            ZoneId.of(sync.getSourceZoneId());
        } catch (DateTimeException exception) {
            throw new IllegalStateException("工单搜索 source-zone-id 必须为有效业务时区");
        }
        // Search 只承担搜索事件的消费职责，禁止同时启动 Outbox Relay，避免配置混用。
        if (messaging.getOutbox().isEnabled()) {
            throw new IllegalStateException("Search-Service 工单搜索消费要求 messaging.outbox.enabled=false");
        }
        return Boolean.TRUE;
    }

    /**
     * 校验MQ名称。
     *
     * @param value 待处理的值
     * @param maxLength 整型数值，对应 maxLength
     * @param field 待读取或校验的字段名
     */
    private static void validateMqName(String value, int maxLength, String field) {
        if (value == null || value.length() > maxLength || !MQ_NAME.matcher(value).matches()) {
            throw new IllegalStateException("工单搜索 " + field + " 格式无效");
        }
    }

    /**
     * @author Virgor
     * @date 2026年10月07日
     * @description 同步显式开启时创建回源、转换和消费组件，导入由持久管理任务触发。
     */
    @Configuration
    @EnableTransactionManagement
    @ConditionalOnProperty(prefix = "work-order.elasticsearch.ticket-sync", name = "enabled", havingValue = "true")
    @DependsOn("ticketSearchSyncRequirements")
    static class EnabledConfiguration {
        /**
         * 保证 DATETIME 按配置时区转换。
         *
         * @param sync 同步
         * @return 工单搜索文档转换器
         */
        @Bean
        TicketSearchDocumentConverter ticketSearchDocumentConverter(TicketSearchSyncProperties sync) {
            return new TicketSearchDocumentConverter(ZoneId.of(sync.getSourceZoneId()));
        }

        /**
         * 缺少主库或事务管理器时启动失败；短事务每次读取共享目标。
         *
         * @param mapper 工单搜索写入目标数据访问器
         * @param dataSource 数据库数据源
         * @param transactionManager 事务Manager
         * @return 工单搜索写入目标Resolver
         */
        @Bean
        TicketSearchWriteTargetResolver ticketSearchWriteTargetResolver(TicketSearchWriteTargetMapper mapper,
                                                                        DataSource dataSource,
                                                                        PlatformTransactionManager transactionManager) {
            return new TicketSearchWriteTargetResolver(mapper);
        }

        /**
         * 统一投影入口；代理暂停外层事务，ES 网络请求不占用数据库事务。
         *
         * @param mapper 工单索引源数据数据访问器
         * @param converter 转换器
         * @param resolver 工单搜索写入目标Resolver，对应 resolver
         * @param indexService 工单搜索索引服务
         * @param repository 工单搜索仓储
         * @return 工单投影服务
         */
        @Bean
        TicketProjectionService ticketProjectionService(TicketIndexSourceMapper mapper,
                                                        TicketSearchDocumentConverter converter,
                                                        TicketSearchWriteTargetResolver resolver,
                                                        TicketSearchIndexService indexService,
                                                        TicketSearchRepository repository) {
            return new TicketProjectionService(mapper, converter, resolver, indexService, repository);
        }

        /**
         * RocketMQ Starter 根据类上的订阅注解注册消费者。
         *
         * @param projectionService 工单投影服务
         * @param listenerContainers 监听器Containers
         * @return 工单搜索Changed监听器
         */
        @Bean
        TicketSearchChangedListener ticketSearchChangedListener(TicketProjectionService projectionService,
                                                                ListenerContainerConfiguration listenerContainers) {
            return new TicketSearchChangedListener(projectionService);
        }
    }
}
