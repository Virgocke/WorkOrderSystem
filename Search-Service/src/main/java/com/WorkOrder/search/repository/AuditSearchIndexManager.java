package com.WorkOrder.search.repository;

import com.WorkOrder.search.config.AuditSearchProperties;
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
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** 管理审计索引的创建、别名和映射校验，由仓库内部使用。 */
final class AuditSearchIndexManager {

    private final RestHighLevelClient client;
    private final ElasticsearchProperties elasticsearchProperties;
    private final AuditSearchProperties auditProperties;

    /** 复用共享客户端与现有配置，构造时不访问 ES。 */
    AuditSearchIndexManager(RestHighLevelClient client, ElasticsearchProperties elasticsearchProperties,
                            AuditSearchProperties auditProperties) {
        this.client = client;
        this.elasticsearchProperties = elasticsearchProperties;
        this.auditProperties = auditProperties;
    }

    /** 优先使用合法的已有别名，否则原子创建索引及别名；不接管或修改已有索引。 */
    String initializeIndex() throws IOException {
        Assert.isTrue(!auditProperties.getIndexName().equals(auditProperties.getIndexAlias()),
                "审计物理索引名称不能与索引别名相同");

        // 尝试使用别名查找现有索引
        String existingIndex = findIndexByAlias();
        if (existingIndex != null) {
            validateMapping(existingIndex);
            return existingIndex;
        }

        String indexName = auditProperties.getIndexName();
        if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
            // 另一实例可能在上次检查后，同时创建了物理索引和别名。
            String concurrentlyCreatedIndex = findIndexByAlias();
            if (concurrentlyCreatedIndex != null) {
                validateMapping(concurrentlyCreatedIndex);
                return concurrentlyCreatedIndex;
            }
            throw new IOException("审计物理索引已存在但缺少别名，请核对后手动配置别名");
        }

        createIndex();
        String initializedIndex = findIndexByAlias();
        if (initializedIndex == null) {
            throw new IOException("审计索引创建后未找到唯一写别名");
        }
        validateMapping(initializedIndex);
        return initializedIndex;
    }

    /** 从 V1 映射创建索引和写别名；并发创建冲突仅在别名合法时允许继续。 */
    private void createIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(auditProperties.getIndexName());
        try (InputStream input = new ClassPathResource("elasticsearch/audit-index-v1.json").getInputStream()) {
            request.source(StreamUtils.copyToString(input, StandardCharsets.UTF_8), XContentType.JSON);
        }
        request.settings(Settings.builder()
                .put("index.number_of_shards", elasticsearchProperties.getNumberOfShards())
                .put("index.number_of_replicas", elasticsearchProperties.getNumberOfReplicas()));
        request.alias(new Alias(auditProperties.getIndexAlias()).writeIndex(true));

        try {
            CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
            if (!response.isAcknowledged() || !response.isShardsAcknowledged()) {
                throw new IOException("审计索引创建尚未确认，不能启用审计搜索");
            }
        } catch (ElasticsearchStatusException exception) {
            if (exception.status() != RestStatus.BAD_REQUEST || findIndexByAlias() == null) {
                throw exception;
            }
        }
    }

    /** 别名缺失时返回 null；存在时必须唯一、可写，并且不附带过滤或路由条件。 */
    private String findIndexByAlias() throws IOException {
        String alias = auditProperties.getIndexAlias();
        GetAliasesResponse response = client.indices().getAlias(new GetAliasesRequest(alias), RequestOptions.DEFAULT);
        if (response.status() == RestStatus.NOT_FOUND) {
            return null;
        }
        if (response.status() != RestStatus.OK || response.getAliases().size() != 1) {
            throw new IOException("审计别名必须只指向一个物理索引");
        }

        Map.Entry<String, Set<AliasMetadata>> indexEntry = response.getAliases().entrySet().iterator().next();
        AliasMetadata aliasMetadata = indexEntry.getValue().stream()
                .filter(metadata -> alias.equals(metadata.alias()))
                .findFirst()
                .orElse(null);
        if (aliasMetadata == null || Boolean.FALSE.equals(aliasMetadata.writeIndex())
                || aliasMetadata.filter() != null || aliasMetadata.indexRouting() != null
                || aliasMetadata.searchRouting() != null) {
            throw new IOException("审计别名必须可写且不带过滤或路由条件");
        }
        return indexEntry.getKey();
    }

    /** 校验严格映射、完整 _source 和各字段约定；不修改现有映射。 */
    private void validateMapping(String indexName) throws IOException {
        MappingMetadata mapping = client.indices().getMapping(new GetMappingsRequest().indices(indexName),
                RequestOptions.DEFAULT).mappings().get(indexName);
        Map<String, Object> definition = mapping == null ? null : mapping.sourceAsMap();
        Object properties = definition == null ? null : definition.get("properties");
        if (definition == null || !"strict".equals(definition.get("dynamic")) || !(properties instanceof Map)) {
            throw new IOException("审计索引缺少约定的 strict 映射，请使用审计 V1 映射重建");
        }

        Object sourceDefinition = definition.get("_source");
        if (sourceDefinition instanceof Map
                && Boolean.FALSE.equals(((Map<?, ?>) sourceDefinition).get("enabled"))) {
            throw new IOException("审计索引必须保留 _source 以读取稳定来源标识");
        }

        Map<?, ?> fields = (Map<?, ?>) properties;
        for (String field : new String[]{"id", "kind", "source", "configKeyExact"}) {
            validateField(fields, field, "keyword", true);
        }
        for (String field : new String[]{"sourceId", "ticketId", "operatorId"}) {
            validateField(fields, field, "long", true);
        }
        for (String field : new String[]{"action", "content", "configKey", "beforeValue", "afterValue"}) {
            validateField(fields, field, "wildcard", false);
        }
        Map<?, ?> dateMapping = validateField(fields, "createdAt", "date", true);
        if (!"epoch_millis".equals(dateMapping.get("format"))) {
            throw new IOException("审计 createdAt 必须支持约定的 epoch_millis 格式");
        }
    }

    /** 校验字段类型及查询、排序能力，拒绝改变字面值或截断内容的额外配置。 */
    private Map<?, ?> validateField(Map<?, ?> fields, String field, String expectedType, boolean needsDocValues)
            throws IOException {
        Object definition = fields.get(field);
        if (!(definition instanceof Map)) {
            throw incompatibleField(field);
        }

        Map<?, ?> fieldMapping = (Map<?, ?>) definition;
        boolean typeMatches = expectedType.equals(fieldMapping.get("type"));
        boolean indexed = !Boolean.FALSE.equals(fieldMapping.get("index"));
        boolean sortable = !needsDocValues || !Boolean.FALSE.equals(fieldMapping.get("doc_values"));
        boolean preservesLiteral = fieldMapping.get("normalizer") == null && fieldMapping.get("ignore_above") == null;
        if (!typeMatches || !indexed || !sortable || !preservesLiteral) {
            throw incompatibleField(field);
        }
        return fieldMapping;
    }

    /** 统一返回字段映射不兼容的错误，避免各校验分支重复错误文案。 */
    private IOException incompatibleField(String field) {
        return new IOException("审计索引字段 " + field + " 映射不兼容，请使用审计 V1 映射重建");
    }
}
