package com.WorkOrder.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.List;

/** Elasticsearch 连接及索引配置。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "work-order.elasticsearch")
public class ElasticsearchProperties {

    /** 是否注册 Elasticsearch 网络组件；关闭时关键词入口仍存在并返回未就绪。 */
    private boolean enabled;

    /** 非空节点地址列表，默认连接本机 9200；每个节点使用无内嵌凭据的 http/https URI。 */
    @NotEmpty
    private List<@NotBlank String> uris =
            Collections.singletonList("http://localhost:9200");

    /** Basic 认证用户名，默认空；启用认证时必须同时提供非空密码。 */
    private String username = "";

    /** Basic 认证密码，默认空；通过外部配置注入，不参与对象的自动 toString 输出。 */
    private String password = "";

    /** 建立 TCP 连接的超时时间，单位毫秒，默认 3000，必须大于零。 */
    @Min(1)
    private int connectTimeoutMs = 3000;

    /** 等待套接字响应数据的超时时间，单位毫秒，默认 10000，必须大于零。 */
    @Min(1)
    private int socketTimeoutMs = 10000;

    /** 从连接池获取可用连接的超时时间，单位毫秒，默认 1000，必须大于零。 */
    @Min(1)
    private int connectionRequestTimeoutMs = 1000;

    /** 连接池允许的总连接数，默认 50，必须大于零。 */
    @Min(1)
    private int maxConnections = 50;

    /** 单个节点允许的最大连接数，默认 25，不能超过总连接数。 */
    @Min(1)
    private int maxConnectionsPerRoute = 25;

    /** 是否在启动后初始化缺失的索引和别名，默认关闭，且仅在组件启用时生效。 */
    private boolean initializeOnStartup;

    /** 首次创建的物理索引名，默认 wo-ticket-v2，采用 IK 映射；已有别名只复用兼容的映射。 */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*")
    private String ticketIndexName = "wo-ticket-v2";

    /** 业务读别名，默认 wo-ticket；V3 写入仅使用任务专属别名。 */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*")
    private String ticketIndexAlias = "wo-ticket";

    /** 用户和处理人共用目录读别名；完整投影写入后原子切换，避免暴露半轮同步。 */
    @NotBlank
    @Size(max = 180)
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*")
    private String directoryIndexAlias = "wo-directory";

    /** 目录物理索引前缀，每个变更快照使用独立 UUID 后缀。 */
    @NotBlank
    @Size(max = 180)
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*")
    private String directoryIndexPrefix = "wo-directory-v1";

    /** 首次建索引的主分片数，默认 1，必须大于零，不自动修改已有索引。 */
    @Min(1)
    private int numberOfShards = 1;

    /** 首次建索引的每主分片副本数，默认 0，适用于本地单节点，不能为负。 */
    @Min(0)
    private int numberOfReplicas;

    /** 单次批量写入的文档数量上限，默认 1000，允许 1 至 5000，调用方自行拆分批次。 */
    @Min(1)
    @Max(5000)
    private int maxBulkSize = 1000;
}
