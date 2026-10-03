package com.WorkOrder.search.repository;

import com.WorkOrder.dashboard.dto.AuditQuery;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.search.config.AuditSearchProperties;
import com.WorkOrder.search.config.ElasticsearchProperties;
import com.WorkOrder.search.model.AuditSearchDocument;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 审计 ES 访问入口：组装搜索条件和写入投影，索引管理与分页读取交给内部组件。 */
public class AuditSearchRepository {

    /** 工单类审计投影标识。 */
    public static final String TICKET_KIND = "TICKET";

    /** 配置类审计投影标识及来源。 */
    public static final String CONFIGURATION_KIND = "CONFIGURATION";

    /** 操作日志来源。 */
    public static final String OPERATION_SOURCE = "OPERATION";

    /** 状态变更日志来源。 */
    public static final String STATUS_SOURCE = "STATUS";

    /** 源库本地时间采用上海时区，不依赖服务进程默认时区。 */
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Shanghai");

    /** ES 单个 terms 条件的默认条数上限，大集合拆分成 OR 条件。 */
    private static final int TERMS_CHUNK_SIZE = 65536;

    /** 7.12.1 高层客户端需显式添加此 HTTP 参数，确保批量写入目标是别名。 */
    private static final RequestOptions ALIAS_WRITE_OPTIONS = RequestOptions.DEFAULT.toBuilder()
            .addParameter("require_alias", "true")
            .build();

    private final RestHighLevelClient client;
    private final AuditSearchProperties properties;
    private final AuditSearchIndexManager indexManager;
    private final AuditSearchPageReader pageReader;

    /** 创建内部组件，复用共享客户端；构造时不访问 ES 或导入源数据。 */
    public AuditSearchRepository(RestHighLevelClient client, ElasticsearchProperties elasticsearchProperties,
                                 AuditSearchProperties properties) {
        this.client = client;
        this.properties = properties;
        this.indexManager = new AuditSearchIndexManager(client, elasticsearchProperties, properties);
        this.pageReader = new AuditSearchPageReader(client);
    }

    /** 创建缺失索引，或校验已有别名和映射；不重建索引或切换已有别名。 */
    public String initializeIndex() throws IOException {
        return indexManager.initializeIndex();
    }

    /** 全批校验后幂等写入并立即刷新；写入或刷新未确认时，不允许同步器推进游标。 */
    public void bulkSave(List<AuditSearchDocument> documents) throws IOException {
        Assert.notNull(documents, "审计投影批次不能为空");
        Assert.isTrue(documents.size() <= properties.getBatchSize(), "审计投影批次超过配置上限");
        if (documents.isEmpty()) {
            return;
        }

        // 构造批量写入请求
        BulkRequest request = buildBulkRequest(documents);
        // 执行批量写入请求
        BulkResponse response = client.bulk(request, ALIAS_WRITE_OPTIONS);
        // 校验批量写入结果
        requireCompleteBulkWrite(response, documents.size());
    }

    /** 校验全部文档及批内 ID 唯一性，写入后立即刷新以保证本批可被搜索。 */
    private BulkRequest buildBulkRequest(List<AuditSearchDocument> documents) {
        BulkRequest request = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        Set<String> documentIds = new HashSet<>();

        for (AuditSearchDocument document : documents) {
            IndexRequest indexRequest = toIndexRequest(document);
            Assert.isTrue(documentIds.add(indexRequest.id()), "同一审计批次不能包含重复来源 ID");
            request.add(indexRequest);
        }
        return request;
    }

    /** 检查每条写入及刷新结果，异常只包含来源 ID 和计数，不回显审计正文。 */
    private void requireCompleteBulkWrite(BulkResponse response, int expectedCount) throws IOException {
        if (response.getItems().length != expectedCount) {
            throw new IOException("审计批量写入的响应数量不完整");
        }

        List<String> failedIds = new ArrayList<>();
        for (BulkItemResponse item : response.getItems()) {
            if (!isWriteVisible(item)) {
                failedIds.add(item.getId());
            }
        }
        if (!failedIds.isEmpty()) {
            throw new IOException("审计批量写入或刷新未确认，失败条数=" + failedIds.size()
                    + "，来源 ID=" + failedIds);
        }
    }

    /** 只有写入成功、至少一个分片成功且强制刷新已确认，才认为该条记录可见。 */
    private boolean isWriteVisible(BulkItemResponse item) {
        if (item.isFailed()) {
            return false;
        }
        DocWriteResponse response = item.getResponse();
        return response != null && response.getShardInfo() != null
                && response.getShardInfo().getFailed() == 0
                && response.getShardInfo().getSuccessful() >= 1
                && response.forcedRefresh();
    }

    /**
     * 所有条件在 ES 分页前生效，结果仅包含准确总数和回表所需的来源标识。
     *
     * @param configurations 是否搜索配置日志
     * @param query 已规范化的条件，关键字保留原始字面值
     * @param operatorIds 数据库按当前姓名筛出的操作人 ID；null 不限制，空集合不匹配
     * @param scopeIds 工单查询为工单 ID，配置查询为日志 ID；配置键比较以数据库排序规则为准
     * @return 搜索结果仅包含来源标识和准确总数
     */
    public PageResult<AuditSearchDocument> search(boolean configurations, AuditQuery query,
                                                List<Long> operatorIds, List<Long> scopeIds) throws IOException {
        Assert.notNull(query, "审计查询不能为空");
        Assert.isTrue(query.getPage() >= 1 && query.getPageSize() >= 1 && query.getPageSize() <= 100,
                "审计分页参数无效");

        BoolQueryBuilder filters = buildFilters(configurations, query, operatorIds, scopeIds);
        SearchSourceBuilder searchSource = buildSearchSource(filters);
        return pageReader.readPage(properties.getIndexAlias(), searchSource, query);
    }

    /** 按种类、字面关键字、可信 ID 集合和时间范围构造筛选条件。 */
    private BoolQueryBuilder buildFilters(boolean configurations, AuditQuery query,
                                         List<Long> operatorIds, List<Long> scopeIds) {
        String kind = configurations ? CONFIGURATION_KIND : TICKET_KIND;
        BoolQueryBuilder filters = QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("kind", kind));
        addKeywordFilter(filters, configurations, query.getKeyword());

        // 调用方提供日志 ID 时，配置键已由 MySQL 比较，不能再追加大小写敏感的 ES term。
        if (configurations && scopeIds == null && StringUtils.hasText(query.getConfigKey())) {
            filters.filter(QueryBuilders.termQuery("configKeyExact", query.getConfigKey()));
        }
        addIdFilter(filters, "operatorId", operatorIds);
        addIdFilter(filters, configurations ? "sourceId" : "ticketId", scopeIds);
        addTimeFilter(filters, query);
        return filters;
    }

    /** 工单匹配动作或正文，配置匹配键或前后值；通配符按字面值转义。 */
    private void addKeywordFilter(BoolQueryBuilder filters, boolean configurations, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String pattern = "*" + escapeWildcard(keyword) + "*";
        String[] searchableFields = configurations
                ? new String[]{"configKey", "beforeValue", "afterValue"}
                : new String[]{"action", "content"};
        BoolQueryBuilder keywordMatches = QueryBuilders.boolQuery().minimumShouldMatch(1);
        for (String field : searchableFields) {
            keywordMatches.should(QueryBuilders.wildcardQuery(field, pattern).caseInsensitive(true));
        }
        filters.filter(keywordMatches);
    }

    /** 时间采用上海时区，开始时间包含边界，结束时间使用规范化后的排他边界。 */
    private void addTimeFilter(BoolQueryBuilder filters, AuditQuery query) {
        if (query.getStartAt() == null && query.getEndBefore() == null) {
            return;
        }
        RangeQueryBuilder createdAtRange = QueryBuilders.rangeQuery("createdAt");
        if (query.getStartAt() != null) {
            createdAtRange.gte(query.getStartAt().atZone(SOURCE_ZONE).toInstant().toEpochMilli());
        }
        if (query.getEndBefore() != null) {
            createdAtRange.lt(query.getEndBefore().atZone(SOURCE_ZONE).toInstant().toEpochMilli());
        }
        filters.filter(createdAtRange);
    }

    /** 只读取来源元数据，并与源库保持相同的稳定排序和 NULL 时间位置。 */
    private SearchSourceBuilder buildSearchSource(BoolQueryBuilder filters) {
        return new SearchSourceBuilder()
                .query(filters)
                .trackTotalHits(true)
                .fetchSource(new String[]{"id", "kind", "source", "sourceId"}, null)
                .sort(new FieldSortBuilder("createdAt").order(SortOrder.DESC).missing("_last"))
                .sort("source", SortOrder.DESC)
                .sort("sourceId", SortOrder.DESC);
    }

    /** null 表示不限制，空集合表示无匹配；大集合拆分以满足 ES terms 条数限制。 */
    private void addIdFilter(BoolQueryBuilder filters, String field, List<Long> ids) {
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            filters.filter(new MatchNoneQueryBuilder());
            return;
        }

        BoolQueryBuilder idMatches = QueryBuilders.boolQuery().minimumShouldMatch(1);
        int chunkStart = 0;
        while (chunkStart < ids.size()) {
            int chunkEnd = (int) Math.min((long) chunkStart + TERMS_CHUNK_SIZE, ids.size());
            idMatches.should(QueryBuilders.termsQuery(field, ids.subList(chunkStart, chunkEnd)));
            chunkStart = chunkEnd;
        }
        filters.filter(idMatches);
    }

    /** 只转义 ES 的星号、问号和反斜杠，百分号、下划线和叹号保持字面值。 */
    private String escapeWildcard(String keyword) {
        return keyword.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }

    /** 校验来源后组装完整投影，稳定 ID 使重复同步成为覆盖写入。 */
    private IndexRequest toIndexRequest(AuditSearchDocument document) {
        validateDocument(document);
        Map<String, Object> fields = buildDocumentFields(document);
        String documentId = document.getSource() + ":" + document.getSourceId();
        return new IndexRequest(properties.getIndexAlias()).id(documentId).source(fields);
    }

    /** 校验日志种类、主键、工单 ID 和操作人 ID，拒绝不一致的来源标识。 */
    private void validateDocument(AuditSearchDocument document) {
        Assert.notNull(document, "审计投影不能为空");
        Assert.isTrue(document.getSourceId() != null && document.getSourceId() > 0, "审计来源 ID 无效");

        boolean ticketAudit = TICKET_KIND.equals(document.getKind());
        boolean validTicketSource = ticketAudit && (OPERATION_SOURCE.equals(document.getSource())
                || STATUS_SOURCE.equals(document.getSource()));
        boolean validConfigurationSource = CONFIGURATION_KIND.equals(document.getKind())
                && CONFIGURATION_KIND.equals(document.getSource());
        Assert.isTrue(validTicketSource || validConfigurationSource, "审计种类与来源不一致");
        if (ticketAudit) {
            Assert.isTrue(document.getTicketId() != null && document.getTicketId() > 0,
                    "工单审计缺少有效工单 ID");
        }
        Assert.isTrue(document.getOperatorId() == null || document.getOperatorId() >= 0, "审计操作人 ID 无效");
        String documentId = document.getSource() + ":" + document.getSourceId();
        Assert.isTrue(document.getId() == null || documentId.equals(document.getId()), "审计文档 ID 与来源不一致");
    }

    /** 共用身份和时间字段，按日志种类写入对应正文；NULL 时间不写入日期字段。 */
    private Map<String, Object> buildDocumentFields(AuditSearchDocument document) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", document.getSource() + ":" + document.getSourceId());
        fields.put("kind", document.getKind());
        fields.put("source", document.getSource());
        fields.put("sourceId", document.getSourceId());
        fields.put("operatorId", document.getOperatorId() == null ? 0L : document.getOperatorId());
        if (document.getCreatedAt() != null) {
            fields.put("createdAt", document.getCreatedAt().atZone(SOURCE_ZONE).toInstant().toEpochMilli());
        }

        if (TICKET_KIND.equals(document.getKind())) {
            fields.put("ticketId", document.getTicketId());
            fields.put("action", document.getAction());
            fields.put("content", document.getContent());
        } else {
            fields.put("configKey", document.getConfigKey());
            fields.put("configKeyExact", document.getConfigKey());
            fields.put("beforeValue", document.getBeforeValue());
            fields.put("afterValue", document.getAfterValue());
        }
        return fields;
    }
}
