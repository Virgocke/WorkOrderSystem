package com.WorkOrder.search.repository;

import com.WorkOrder.search.config.ElasticsearchProperties;
import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.exception.TicketSearchBulkException;
import com.WorkOrder.search.model.TicketSearchBulkResult;
import com.WorkOrder.search.model.TicketSearchWriteOutcome;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import com.WorkOrder.search.model.TicketSearchStoredVersion;
import com.WorkOrder.search.support.ElasticsearchFailureClassifier;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 内部搜索存储能力；不访问 MySQL、不发布消息、不承担业务授权。
 * 写入只接受服务端解析的受管目标，以源版本原子拒绝重复或乱序旧投影。
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
     * 使用稳定工单 ID 将完整搜索投影写入受管别名，以 external 源版本进行原子仲裁。
     * 未提供的可选字段会从旧文档移除；缺失别名时服务端拒绝写入，不自动创建物理索引。
     * 只有明确的版本冲突视为已覆盖；其他异常继续向同步调用方传播。
     *
     * @param target 服务端解析、验证后的受管写目标
     * @param document 完整搜索投影，必填字段完整且源版本大于零
     * @return 实际写入或已被相同、更高版本覆盖
     * @throws IllegalArgumentException 文档为空或必填字段缺失
     * @throws IOException 文档序列化失败、网络通信失败或响应解析失败
     */
    public TicketSearchWriteOutcome save(TicketSearchWriteTarget target, TicketSearchDocument document)
            throws IOException {
        IndexRequest request = toIndexRequest(target, document);
        try {
            client.index(request, ALIAS_WRITE_OPTIONS);
            return TicketSearchWriteOutcome.APPLIED;
        } catch (ElasticsearchStatusException exception) {
            if (isVersionConflict(exception.status(), exception)) {
                // 本请求采用 EXTERNAL；明确版本冲突表示 ES 已保存相同或更高版本，可确认覆盖。
                return TicketSearchWriteOutcome.COVERED;
            }
            throw exception;
        }
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
     * 实时读取固定代次的批量版本，只取 ID 和 sourceVersion，不依赖搜索刷新。
     * 文档缺失返回 found=false；分片错误、响应缺项、目标错配及版本协议破坏均抛出异常。
     *
     * @param target 当前批次已经固定并校验的受管写目标
     * @param ticketIds 正整数工单 ID，不允许重复，数量不超过批次上限
     * @return 按工单 ID 保存的只读版本记录
     * @throws IOException 网络错误、单项失败、响应不完整或存储版本不符合协议
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<Long, TicketSearchStoredVersion> multiGetVersions(TicketSearchWriteTarget target,
                                                                List<Long> ticketIds) throws IOException {
        validateTarget(target);
        Assert.notNull(ticketIds, "工单版本查询 ID 列表不能为空");
        Assert.isTrue(ticketIds.size() <= properties.getMaxBulkSize(), "工单版本查询数量超过配置上限");
        if (ticketIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 实时 MGET 可看到已写入但尚未 refresh 的文档，核对时不必主动刷新每个批次。
        MultiGetRequest request = new MultiGetRequest().realtime(true).refresh(false);
        Map<String, Long> requested = new LinkedHashMap<>();
        for (Long ticketId : ticketIds) {
            Assert.isTrue(ticketId != null && ticketId > 0, "工单版本查询 ID 必须为正数");
            Assert.isTrue(requested.put(ticketId.toString(), ticketId) == null, "工单版本查询不能包含重复 ID");
            request.add(new MultiGetRequest.Item(target.getWriteAlias(), ticketId.toString())
                    .fetchSourceContext(new FetchSourceContext(true,
                            new String[]{"ticketId", "sourceVersion"}, new String[0])));
        }
        MultiGetResponse response = client.mget(request, RequestOptions.DEFAULT);
        MultiGetItemResponse[] items = response.getResponses();
        if (items == null || items.length != ticketIds.size()) {
            throw new IOException("Elasticsearch 工单版本查询返回数量与请求不一致");
        }
        Map<Long, TicketSearchStoredVersion> versions = new LinkedHashMap<>();
        for (MultiGetItemResponse item : items) {
            Long ticketId = item == null ? null : requested.get(item.getId());
            if (ticketId == null || versions.containsKey(ticketId)) {
                throw new IOException("Elasticsearch 工单版本查询包含未请求或重复 ID");
            }
            // 读取失败和文档缺失必须区分；不能把分片错误当作“未找到”继续推进验证进度。
            if (item.isFailed()) {
                throw new IOException("Elasticsearch 工单版本查询单项失败，ticketId=" + ticketId,
                        item.getFailure().getFailure());
            }
            GetResponse document = item.getResponse();
            if (document == null || !target.getPhysicalIndex().equals(document.getIndex())) {
                throw new IOException("Elasticsearch 工单版本查询目标与登记索引不一致，ticketId=" + ticketId);
            }
            if (!document.isExists()) {
                versions.put(ticketId, new TicketSearchStoredVersion(0, null, false));
            } else {
                versions.put(ticketId, readStoredVersion(ticketId, document));
            }
        }
        return Collections.unmodifiableMap(versions);
    }

    /** 只承认完整投影协议中的数值版本和稳定字符串 ID，不掩盖内部版本写入造成的错配。 */
    private TicketSearchStoredVersion readStoredVersion(Long ticketId, GetResponse document) throws IOException {
        String source = document.getSourceAsString();
        JsonNode fields = source == null ? null : documentMapper.readTree(source);
        JsonNode storedId = fields == null ? null : fields.get("ticketId");
        JsonNode storedVersion = fields == null ? null : fields.get("sourceVersion");
        // EXTERNAL 协议下 ES _version 应等于源版本，错配说明文档不能用于判断已覆盖。
        if (storedId == null || !storedId.isTextual() || !ticketId.toString().equals(storedId.asText())
                || storedVersion == null || !storedVersion.isIntegralNumber() || !storedVersion.canConvertToLong()
                || storedVersion.longValue() <= 0 || document.getVersion() != storedVersion.longValue()) {
            throw new IOException("Elasticsearch 工单投影 ID 或源版本与元数据版本不一致，ticketId=" + ticketId);
        }
        return new TicketSearchStoredVersion(document.getVersion(), storedVersion.longValue(), true);
    }

    /**
     * 全部校验后提交一个批次；部分失败抛出携带失败 ID 的异常，已成功项不会回滚。
     * 空列表不发送请求，不自动拆批或重试；调用方按 max-bulk-size 拆分并根据异常恢复。
     * 每条文档采用与 save 相同的完整替换规则，网络异常不代表服务端完全未写入。
     *
     * @param target 服务端解析、验证后的受管写目标
     * @param documents 同一批次的完整搜索投影，列表和元素不能为 null，工单 ID 不得重复
     * @return 包含实际写入及版本覆盖数量的结果，正常返回时没有真正失败项
     * @throws IllegalArgumentException 数量超过配置上限、文档字段缺失或同一批次包含重复工单 ID
     * @throws TicketSearchBulkException 服务端返回逐条失败，异常包含成功数量及失败 ID 和原因
     * @throws IOException 文档序列化失败、网络通信失败或批量响应解析失败
     */
    public TicketSearchBulkResult bulkSave(TicketSearchWriteTarget target, List<TicketSearchDocument> documents)
            throws IOException {
        validateTarget(target);
        Assert.notNull(documents, "批量文档不能为空");
        Assert.isTrue(documents.size() <= properties.getMaxBulkSize(), "批量文档数量超过配置上限");
        if (documents.isEmpty()) {
            return new TicketSearchBulkResult(0, 0, java.util.Collections.emptyMap());
        }
        // 构建批量请求
        BulkRequest request = new BulkRequest();
        Set<String> ids = new HashSet<>();
        // 遍历文档列表，添加到批量请求中
        for (TicketSearchDocument document : documents) {
            IndexRequest item = toIndexRequest(target, document);
            Assert.isTrue(ids.add(item.id()), "同一批次不能包含重复工单 ID");
            request.add(item);
        }
        // 执行批量请求
        BulkResponse response = client.bulk(request, ALIAS_WRITE_OPTIONS);
        if (response.getItems().length != documents.size()) {
            throw new IOException("Elasticsearch Bulk 返回数量与请求不一致，不能确认批次完成");
        }
        Map<String, TicketSearchBulkResult.Failure> failures = new LinkedHashMap<>();
        int appliedCount = 0;
        int coveredCount = 0;
        Set<String> returnedIds = new HashSet<>();
        // 遍历批量响应，收集失败项
        for (BulkItemResponse item : response.getItems()) {
            if (!ids.contains(item.getId()) || !returnedIds.add(item.getId())) {
                throw new IOException("Elasticsearch Bulk 返回未请求或重复 ID，不能确认批次完成");
            }
            if (!item.isFailed()) {
                appliedCount++;
            } else if (isVersionConflict(item.status(), item.getFailure().getCause())) {
                // 旧批次或重复消息无需再次写入，已有相同或更高版本也算本项完成。
                coveredCount++;
            } else {
                failures.put(item.getId(), new TicketSearchBulkResult.Failure(item.status().getStatus(),
                        ElasticsearchFailureClassifier.errorType(item.getFailure().getCause()), item.getFailureMessage()));
            }
        }
        TicketSearchBulkResult result = new TicketSearchBulkResult(appliedCount, coveredCount, failures);
        if (!failures.isEmpty()) {
            // Bulk 不会回滚成功项；让任务保留原游标重试，重复项由版本仲裁安全跳过。
            throw new TicketSearchBulkException(result);
        }
        return result;
    }

    /**
     * 按关键字、精确过滤和创建时间范围查询搜索投影，按约定字段及稳定次序分页。
     * 关键字精确匹配编号或分词匹配标题、描述；无关键字时仍应用其他过滤条件。
     * 创建时间支持单边或双边范围且包含边界，所有筛选和排序均在分页前执行。
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

        String sort = StringUtils.hasText(query.getSort()) ? query.getSort().trim() : "createdAt";
        Assert.isTrue("deadline".equals(sort) || "priority".equals(sort) || "createdAt".equals(sort),
                "排序方式仅支持 deadline、priority 或 createdAt");

        // 构建查询条件
        BoolQueryBuilder bool = QueryBuilders.boolQuery();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            // 全文查询沿用 V2 映射的 ik_smart；编号仍精确匹配，不参与分词。
            bool.should(QueryBuilders.termQuery("ticketNo", keyword))
                    .should(QueryBuilders.multiMatchQuery(keyword)
                            .field("title", 2.0f)
                            .field("description"))
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
                .trackTotalHits(true);
        // 添加排序条件
        addSort(source, sort);

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
     * 在 ES 分页前添加业务排序及稳定次序；截止时间空值在前，与 MySQL 升序一致。
     * 映射升级前的空字段采用 date 兜底，正常索引初始化会补齐截止时间映射。
     *
     * @param source 搜索源
     * @param sort 排序字段
     */
    private void addSort(SearchSourceBuilder source, String sort) {
        // 按截止时间升序排序，空值在前
        if ("deadline".equals(sort)) {
            source.sort(new FieldSortBuilder("responseDeadline")
                    .order(SortOrder.ASC)
                    .missing("_first")
                    .unmappedType("date"));
        } else if ("priority".equals(sort)) {
            // 按优先级降序排序
            source.sort("priority", SortOrder.DESC);
        }
        // 按创建时间降序排序，同时间创建的工单按 ID 降序
        source.sort("createdAt", SortOrder.DESC)
                .sort("ticketId", SortOrder.DESC);
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
     * 刷新尚未发布的固定任务代次，不读取或刷新业务读别名。
     *
     * @param target 已校验的受管写目标
     * @throws IOException 网络失败或存在失败分片
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refresh(TicketSearchWriteTarget target) throws IOException {
        validateTarget(target);
        RefreshResponse response = client.indices()
                .refresh(new RefreshRequest(target.getWriteAlias()), RequestOptions.DEFAULT);
        if (response.getFailedShards() > 0) {
            throw new IOException("Elasticsearch 受管目标刷新存在失败分片");
        }
    }

    /**
     * 校验完整搜索投影的必填字段，并构建以工单 ID 为 _id 的别名写入请求。
     * 本方法仅构造请求，不发送网络请求；别名保护由调用方传入的请求选项启用。
     *
     * @param target 受管写目标
     * @param document 待保存的完整搜索投影
     * @return 以受管写别名为目标、带 external 源版本及完整 JSON 内容的索引请求
     * @throws IllegalArgumentException 文档为空或 ID、编号、标题、创建时间、更新时间缺失
     * @throws IOException 投影无法序列化为 JSON
     */
    private IndexRequest toIndexRequest(TicketSearchWriteTarget target, TicketSearchDocument document)
            throws IOException {
        validateTarget(target);
        Assert.notNull(document, "搜索文档不能为空");
        Assert.hasText(document.getTicketId(), "工单 ID 不能为空");
        Assert.hasText(document.getTicketNo(), "工单编号不能为空");
        Assert.hasText(document.getTitle(), "工单标题不能为空");
        Assert.notNull(document.getCreatedAt(), "工单创建时间不能为空");
        Assert.notNull(document.getUpdatedAt(), "工单更新时间不能为空");
        Assert.isTrue(document.getSourceVersion() != null && document.getSourceVersion() > 0,
                "工单源版本必须大于零");
        // ES 原子比较源版本并完整替换文档，避免应用层先查再写的并发窗口；旧版本不能覆盖新版本。
        return new IndexRequest(target.getWriteAlias()).id(document.getTicketId())
                .version(document.getSourceVersion()).versionType(VersionType.EXTERNAL)
                .source(documentMapper.writeValueAsString(document), XContentType.JSON);
    }

    /** 校验固定写目标，禁止把业务读别名或物理索引当作增量写入入口。 */
    private void validateTarget(TicketSearchWriteTarget target) {
        Assert.notNull(target, "工单搜索写目标不能为空");
        Assert.hasText(target.getWriteAlias(), "工单搜索写别名不能为空");
        Assert.isTrue(!target.getWriteAlias().equals(alias()), "工单投影不能写入业务读别名");
        Assert.isTrue(!target.getWriteAlias().equals(target.getPhysicalIndex()), "工单投影必须使用受管写别名");
    }

    /** 409 本身不足以表示完成，只接受明确的版本冲突类型。 */
    private boolean isVersionConflict(RestStatus status, Throwable failure) {
        return status == RestStatus.CONFLICT
                && ElasticsearchFailureClassifier.hasType(failure, "version_conflict_engine_exception");
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
     * 获取用于文档读取、业务搜索和显式刷新的业务读别名。
     *
     * @return Elasticsearch 配置中的工单搜索别名
     */
    private String alias() {
        return properties.getTicketIndexAlias();
    }
}
