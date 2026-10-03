package com.WorkOrder.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 审计搜索投影的独立索引与分批轮扫配置，连接及分片配置复用 ElasticsearchProperties。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "work-order.elasticsearch.audit")
public class AuditSearchProperties {

    /** 首次创建的物理索引名称；已有别名保持原指向，不自动重建或切换。 */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*")
    private String indexName = "wo-audit-v1";

    /** 统一读写的审计索引别名，必须与物理索引名不同。 */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*")
    private String indexAlias = "wo-audit";

    /** 每批从源日志读取并写入的条数，允许 1 至 5000 条。 */
    @Min(1)
    @Max(5000)
    private int batchSize = 500;

    /** 一轮同步最多推进的批次数，限制单轮资源占用。 */
    @Min(1)
    @Max(1000)
    private int batchesPerRun = 10;

    /** 后台轮扫同步的调度间隔，单位毫秒，至少 1000 毫秒。 */
    @Min(1000)
    private long syncDelayMs = 30000;

    /** 校验物理索引与别名不会重名，避免写入绕过别名保护。 */
    @AssertTrue(message = "审计物理索引名称不能与索引别名相同")
    public boolean isIndexNamesDistinct() {
        return indexName == null || !indexName.equals(indexAlias);
    }
}
