package com.WorkOrder.search.repository;

import com.WorkOrder.search.config.ElasticsearchProperties;
import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.exception.TicketSearchBulkException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 内部搜索存储能力；不访问 MySQL、不发布消息、不承担业务授权。
 * 保存为完整替换，同一 ID 重复保存不会增加文档；暂不处理乱序事件的版本仲裁。
 */
public class TicketSearchRepository {

    /** 基础分页允许的最大每页记录数。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 基础 from/size 分页窗口上限，页码与每页数量的乘积不能超过此值。 */
    private static final int MAX_RESULT_WINDOW = 10000;

    /**
     * 单条及批量写入共用的请求选项，要求目标必须为索引别名。
     * 7.12.1 的高层请求转换器不传递 requireAlias，因此显式添加 HTTP 参数。
     */
    private static final RequestOptions ALIAS_WRITE_OPTIONS = RequestOptions.DEFAULT.toBuilder()
            .addParameter("require_alias", "true").build();

    /** Spring 容器管理的共享客户端，执行索引、文档和搜索 HTTP 请求。 */
    private final RestHighLevelClient client;

    /** 仓库使用的索引别名和批量写入数量上限配置。 */
    private final ElasticsearchProperties properties;

    /**
     * 搜索文档专用序列化器，不复用 HTTP 响应的全局 Jackson 配置。
     * 日期使用 ISO 字符串并保留偏移量，空字段不写入 JSON。
     */
    private final ObjectMapper documentMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * 创建内部搜索仓库，仅保存依赖，不加载源业务数据或检查远程索引。
     *
     * @param client 由 Spring 容器管理的共享 Elasticsearch 客户端
     * @param properties 已完成绑定和校验的别名及批量写入配置
     */
    public TicketSearchRepository(RestHighLevelClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 使用稳定工单 ID 将完整搜索投影写入别名，已有文档会被整体替换。
     * 未提供的可选字段会从旧文档移除；缺失别名时服务端拒绝写入，不自动创建物理索引。
     * 搜索可见性等待自动刷新，不校验源业务权限和事件版本。
     *
     * @param document 工单搜索投影，必须提供 ID、编号、标题、创建时间和更新时间
     * @throws IllegalArgumentException 文档为空或必填字段缺失
     * @throws IOException 文档序列化失败、网络通信失败或响应解析失败
     */
    public void save(TicketSearchDocument document) throws IOException {
        client.index(toIndexRequest(document), ALIAS_WRITE_OPTIONS);
    }

    /**
     * 通过搜索别名按 ID 实时读取投影，不需要等待搜索刷新。
     * 不存在的文档返回空结果，连接失败等异常继续向调用方抛出。
     *
     * @param ticketId 非空的稳定工单 ID
     * @return 存在时包含工单搜索投影，不存在时为 {@link Optional#empty()}
     * @throws IllegalArgumentException 工单 ID 为空或空白
     * @throws IOException 网络请求失败、响应解析失败或文档内容无法反序列化
     */
    public Optional<TicketSearchDocument> findById(String ticketId) throws IOException {
        Assert.hasText(ticketId, "工单 ID 不能为空");
        GetResponse response = client.get(new GetRequest(alias(), ticketId), RequestOptions.DEFAULT);
        return response.isExists() ? Optional.of(readDocument(response.getSourceAsString())) : Optional.empty();
    }

    /**
     * 按 ID 删除搜索投影，不修改源数据库中的工单。
     * 重复删除不存在的文档返回 false，搜索结果中的删除可见性等待自动刷新。
     *
     * @param ticketId 非空的稳定工单 ID
     * @return 删除已有文档时为 true，文档不存在时为 false
     * @throws IllegalArgumentException 工单 ID 为空或空白
     * @throws IOException 网络请求失败或删除响应解析失败
     */
    public boolean deleteById(String ticketId) throws IOException {
        Assert.hasText(ticketId, "工单 ID 不能为空");
        return client.delete(new DeleteRequest(alias(), ticketId), RequestOptions.DEFAULT)
                .getResult() != DocWriteResponse.Result.NOT_FOUND;
    }

    /**
     * 全部校验后提交一个批次；部分失败抛出携带失败 ID 的异常，已成功项不会回滚。
     * 空列表不发送请求，不自动拆批或重试；调用方按 max-bulk-size 拆分并根据异常恢复。
     * 每条文档采用与 save 相同的完整替换规则，网络异常不代表服务端完全未写入。
     *
     * @param documents 同一批次的完整搜索投影，列表和元素不能为 null，工单 ID 不得重复
     * @throws IllegalArgumentException 数量超过配置上限、文档字段缺失或同一批次包含重复工单 ID
     * @throws TicketSearchBulkException 服务端返回逐条失败，异常包含成功数量及失败 ID 和原因
     * @throws IOException 文档序列化失败、网络通信失败或批量响应解析失败
     */
    public void bulkSave(List<TicketSearchDocument> documents) throws IOException {
        Assert.notNull(documents, "批量文档不能为空");
        Assert.isTrue(documents.size() <= properties.getMaxBulkSize(), "批量文档数量超过配置上限");
        if (documents.isEmpty()) {
            return;
        }
        // 构建批量请求
        BulkRequest request = new BulkRequest();
        Set<String> ids = new HashSet<>();
        // 遍历文档列表，添加到批量请求中
        for (TicketSearchDocument document : documents) {
            IndexRequest item = toIndexRequest(document);
            Assert.isTrue(ids.add(item.id()), "同一批次不能包含重复工单 ID");
            request.add(item);
        }
        // 执行批量请求
        BulkResponse response = client.bulk(request, ALIAS_WRITE_OPTIONS);
        Map<String, String> failures = new LinkedHashMap<>();
        // 遍历批量响应，收集失败项
        for (BulkItemResponse item : response.getItems()) {
            if (item.isFailed()) {
                failures.put(item.getId(), item.getFailureMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new TicketSearchBulkException(response.getItems().length - failures.size(), failures);
        }
    }

    /**
     * 按关键字、精确过滤和创建时间范围查询搜索投影，按创建时间和字符串工单 ID 倒序分页。
     * 关键字精确匹配编号或分词匹配标题、描述；无关键字时仍应用其他过滤条件。
     * 创建时间支持单边或双边范围且包含边界，所有筛选均在分页前执行。
     * 不负责业务授权，调用方需生成可信权限条件；超时或分片失败不返回部分结果。
     *
     * @param query 查询条件，页码从 1 开始，每页 1 至 100 条，分页窗口不超过 10000
     * @return 包含 records、准确 total、page 和 pageSize 的内部搜索结果
     * @throws IllegalArgumentException 条件为空、关键字超过 500 个字符或分页参数不符合限制
     * @throws IOException 请求或响应解析失败、查询超时、分片失败或未取得准确总数
     */
    public TicketSearchPage search(TicketSearchQuery query) throws IOException {
        Assert.notNull(query, "搜索条件不能为空");
        Assert.isTrue(query.getPage() >= 1, "页码必须大于等于 1");
        Assert.isTrue(query.getPageSize() >= 1 && query.getPageSize() <= MAX_PAGE_SIZE,
                "每页数量必须介于 1 和 100 之间");
        long window = (long) query.getPage() * query.getPageSize();
        Assert.isTrue(window <= MAX_RESULT_WINDOW, "基础分页仅支持前 10000 条，请缩小筛选范围");
        Assert.isTrue(query.getKeyword() == null || query.getKeyword().length() <= 500,
                "搜索关键字不能超过 500 个字符");
        Assert.isTrue(query.getStart() == null || query.getEnd() == null
                        || !query.getStart().isAfter(query.getEnd()),
                "创建时间下限不能晚于上限");

        // 构建查询条件
        BoolQueryBuilder bool = QueryBuilders.boolQuery();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            // 全文查询沿用 V2 映射的 ik_smart；编号仍精确匹配，不参与分词。
            bool.should(QueryBuilders.termQuery("ticketNo", keyword))
                    .should(QueryBuilders.multiMatchQuery(keyword).field("title", 2.0f).field("description"))
                    .minimumShouldMatch(1);
        } else {
            bool.must(QueryBuilders.matchAllQuery());
        }

        // 添加过滤条件
        addFilter(bool, "categoryId", query.getCategoryId());
        addFilter(bool, "status", query.getStatus());
        addFilter(bool, "creatorId", query.getCreatorId());
        addFilter(bool, "handlerId", query.getHandlerId());
        addFilter(bool, "slaStatus", query.getSlaStatus());
        if (query.getPriority() != null) {
            bool.filter(QueryBuilders.termQuery("priority", query.getPriority()));
        }
        if (query.getStart() != null || query.getEnd() != null) {
            RangeQueryBuilder createdAt = QueryBuilders.rangeQuery("createdAt");
            if (query.getStart() != null) {
                createdAt.gte(query.getStart().toString());
            }
            if (query.getEnd() != null) {
                createdAt.lte(query.getEnd().toString());
            }
            bool.filter(createdAt);
        }

        // 构建搜索源
        SearchSourceBuilder source = new SearchSourceBuilder()
                .query(bool)
                .from((query.getPage() - 1) * query.getPageSize())
                .size(query.getPageSize())
                .trackTotalHits(true)
                .sort("createdAt", SortOrder.DESC)
                .sort("ticketId", SortOrder.DESC);

        // 执行搜索查询
        SearchResponse response = client
                .search(new SearchRequest(alias())
                .source(source)
                        .allowPartialSearchResults(false), RequestOptions.DEFAULT);

        if (response.isTimedOut() || response.getFailedShards() > 0) {
            throw new IOException("Elasticsearch 查询超时或分片失败，无法返回完整结果");
        }
        TotalHits total = response.getHits().getTotalHits();
        if (total == null || total.relation != TotalHits.Relation.EQUAL_TO) {
            throw new IOException("Elasticsearch 未返回准确的搜索总数");
        }
        List<TicketSearchDocument> records = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            records.add(readDocument(hit.getSourceAsString()));
        }
        return new TicketSearchPage(records, total.value, query.getPage(), query.getPageSize());
    }

    /**
     * 显式刷新别名指向的索引，使已完成的投影写入和删除可被搜索看到。
     * 供调试或离线导入使用，常规业务写入不应逐条调用此方法。
     *
     * @throws IOException 请求或响应解析失败，或刷新响应中存在失败分片
     */
    public void refresh() throws IOException {
        RefreshResponse response = client.indices().refresh(new RefreshRequest(alias()), RequestOptions.DEFAULT);
        if (response.getFailedShards() > 0) {
            throw new IOException("Elasticsearch 刷新存在失败分片");
        }
    }

    /**
     * 校验完整搜索投影的必填字段，并构建以工单 ID 为 _id 的别名写入请求。
     * 本方法仅构造请求，不发送网络请求；别名保护由调用方传入的请求选项启用。
     *
     * @param document 待保存的完整搜索投影
     * @return 以搜索别名为目标、包含 JSON 投影内容的索引请求
     * @throws IllegalArgumentException 文档为空或 ID、编号、标题、创建时间、更新时间缺失
     * @throws IOException 投影无法序列化为 JSON
     */
    private IndexRequest toIndexRequest(TicketSearchDocument document) throws IOException {
        Assert.notNull(document, "搜索文档不能为空");
        Assert.hasText(document.getTicketId(), "工单 ID 不能为空");
        Assert.hasText(document.getTicketNo(), "工单编号不能为空");
        Assert.hasText(document.getTitle(), "工单标题不能为空");
        Assert.notNull(document.getCreatedAt(), "工单创建时间不能为空");
        Assert.notNull(document.getUpdatedAt(), "工单更新时间不能为空");
        return new IndexRequest(alias()).id(document.getTicketId())
                .source(documentMapper.writeValueAsString(document), XContentType.JSON);
    }

    /**
     * 将 Elasticsearch 的 _source JSON 还原为工单搜索投影，保留日期偏移量。
     *
     * @param source 服务端返回的文档源内容，不能为 null
     * @return 反序列化得到的工单搜索投影
     * @throws IOException _source 缺失、JSON 无效或字段无法转换为投影模型
     */
    private TicketSearchDocument readDocument(String source) throws IOException {
        if (source == null) {
            throw new IOException("Elasticsearch 文档缺少 _source");
        }
        return documentMapper.readValue(source, TicketSearchDocument.class);
    }

    /**
     * 为布尔查询追加一个字符串字段的精确过滤条件，空值或空白时不追加。
     * 有效字符串按原值匹配，不进行分词或自动去除首尾空白。
     *
     * @param query 当前正在构建的布尔查询
     * @param field 映射中的 keyword 字段名，由仓库内部指定
     * @param value 精确匹配值，可以为 null 或空白
     */
    private void addFilter(BoolQueryBuilder query, String field, String value) {
        if (StringUtils.hasText(value)) {
            query.filter(QueryBuilders.termQuery(field, value));
        }
    }

    /**
     * 获取仓库统一用于文档读写、搜索和刷新的索引别名。
     *
     * @return Elasticsearch 配置中的工单搜索别名
     */
    private String alias() {
        return properties.getTicketIndexAlias();
    }
}
