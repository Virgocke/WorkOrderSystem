package com.WorkOrder.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 工单投影、历史导入和周期对账配置；默认关闭。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "work-order.elasticsearch.ticket-sync")
public class TicketSearchSyncProperties {
    /** 是否注册工单搜索变更消费者。 */
    private boolean enabled;
    /** 独立消费组，不能复用通知或 SLA 的消费组。 */
    private String consumerGroup = "search-ticket-projection-v1";
    /** tickets 中 DATETIME 的业务时区，不使用 JVM 默认时区。 */
    private String sourceZoneId = "Asia/Shanghai";
    /** 一批主库源行的上限，不能超过 ES max-bulk-size。 */
    private int batchSize = 500;
    /** 每次调度最多处理批次数，避免独占调度线程。 */
    private int batchesPerRun = 10;
    /** 周期对账调度间隔，毫秒。 */
    private long reconcileDelayMs = 30000;
    /** 导入调度间隔，毫秒。 */
    private long importDelayMs = 1000;
    /** 以数据库时钟计算的扫描租约时长，秒。 */
    private int importLeaseSeconds = 120;
}
