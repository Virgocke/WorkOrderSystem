package com.WorkOrder.search.service;

import com.WorkOrder.search.config.ElasticsearchProperties;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** 索引首次初始化及连通性检查，不删除索引、不切换已有别名。 */
public class TicketSearchIndexService {

    /** 首次建索引时加载的类路径资源，定义 V1 工单搜索投影的严格字段映射。 */
    private static final String INDEX_RESOURCE = "elasticsearch/ticket-index-v1.json";

    /** Spring 容器管理的共享客户端，本服务不单独关闭它。 */
    private final RestHighLevelClient client;

    /** 索引名、统一读写别名及首次创建时使用的主分片和副本配置。 */
    private final ElasticsearchProperties properties;

    /**
     * 创建索引管理服务，仅保存依赖，不主动连接节点或创建索引。
     *
     * @param client 由 Spring 容器管理的共享 Elasticsearch 客户端
     * @param properties 已完成绑定和校验的 Elasticsearch 配置
     */
    public TicketSearchIndexService(RestHighLevelClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 使用客户端的 HEAD 探测检查节点连通性，不执行索引读写。
     *
     * @return 客户端收到成功探测响应时为 true，判定节点不可用时为 false
     * @throws IOException 网络通信失败或探测超时
     */
    public boolean ping() throws IOException {
        return client.ping(RequestOptions.DEFAULT);
    }

    /**
     * 创建缺失的索引及写别名，返回别名实际指向的物理索引。
     * 已有物理索引却缺少别名时拒绝绑定，避免接管未知结构的索引。
     * 已有合法别名时直接复用，不修改其指向和映射；并发创建时重新检查别名。
     *
     * @return 搜索别名当前指向的唯一物理索引名，可能是已部署的其他版本
     * @throws IOException 资源读取或网络请求失败、别名不符合约束、索引缺少别名或创建未确认
     * @throws ElasticsearchStatusException 服务端拒绝索引请求且无法通过已存在的合法别名恢复
     */
    public String initializeIndex() throws IOException {
        String existing = findSingleIndex();
        if (existing != null) {
            return existing;
        }
        String indexName = properties.getTicketIndexName();
        if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
            throw new IOException("Elasticsearch 物理索引已存在但缺少搜索别名，请核对映射后手动配置别名");
        }
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        try (InputStream input = new ClassPathResource(INDEX_RESOURCE).getInputStream()) {
            request.source(StreamUtils.copyToString(input, StandardCharsets.UTF_8), XContentType.JSON);
        }
        request.settings(Settings.builder()
                .put("index.number_of_shards", properties.getNumberOfShards())
                .put("index.number_of_replicas", properties.getNumberOfReplicas()));
        request.alias(new Alias(properties.getTicketIndexAlias()).writeIndex(true));
        try {
            CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
            if (!response.isAcknowledged() || !response.isShardsAcknowledged()) {
                throw new IOException("Elasticsearch 索引创建尚未确认完成，请检查集群后重试初始化");
            }
        } catch (ElasticsearchStatusException exception) {
            // 多个实例同时初始化时，另一实例可能已经完成原子建索引和别名。
            if (exception.status() != RestStatus.BAD_REQUEST || findSingleIndex() == null) {
                throw exception;
            }
        }
        String initialized = findSingleIndex();
        if (initialized == null) {
            throw new IOException("Elasticsearch 索引创建后仍未找到搜索别名");
        }
        return initialized;
    }

    /**
     * 查找搜索别名的唯一目标，并校验别名可写且没有过滤和路由条件。
     * 单索引别名未显式设置写标记时可用于写入，显式设置为 false 时拒绝使用。
     *
     * @return 别名指向的物理索引名；服务端返回别名不存在时为 null
     * @throws IOException 请求失败、响应解析失败或别名的目标数量及读写条件不符合要求
     */
    private String findSingleIndex() throws IOException {
        String aliasName = properties.getTicketIndexAlias();
        GetAliasesResponse response = client.indices().getAlias(
                new GetAliasesRequest(aliasName), RequestOptions.DEFAULT);
        if (response.status() == RestStatus.NOT_FOUND) {
            return null;
        }
        Map<String, Set<AliasMetadata>> aliases = response.getAliases();
        if (response.status() != RestStatus.OK || aliases.size() != 1) {
            throw new IOException("Elasticsearch 搜索别名必须只指向一个物理索引");
        }
        Map.Entry<String, Set<AliasMetadata>> entry = aliases.entrySet().iterator().next();
        AliasMetadata metadata = entry.getValue().stream()
                .filter(alias -> aliasName.equals(alias.alias())).findFirst().orElse(null);
        if (metadata == null || Boolean.FALSE.equals(metadata.writeIndex())
                || metadata.filter() != null || metadata.indexRouting() != null
                || metadata.searchRouting() != null) {
            throw new IOException("Elasticsearch 搜索别名必须可写且不带过滤或路由条件");
        }
        return entry.getKey();
    }
}
