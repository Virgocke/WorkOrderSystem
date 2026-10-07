package com.WorkOrder.search.repository;

import com.WorkOrder.search.config.ElasticsearchProperties;
import com.WorkOrder.search.model.DirectorySearchDocument;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 目录快照完整写入后再切换读别名，关键词检索返回所有候选 ID，由业务 SQL 分页。
 */
@Repository
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class DirectorySearchRepository {
    // 滚动查询保活时间
    private static final TimeValue SCROLL_KEEP_ALIVE = TimeValue.timeValueMinutes(1);
    // 搜索超时时间
    private static final long SEARCH_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2);
    // 废弃索引保留时间
    private static final long RETIRE_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private final RestHighLevelClient client;
    private final ElasticsearchProperties properties;
    // 废弃索引缓存
    private final Map<String, Long> retiredIndices = new HashMap<>();

    /**
     * 复用已有连接配置，不在构造阶段创建远程索引。
     *
     * @param client Elasticsearch 高级客户端
     * @param properties Elasticsearch配置属性
     */
    public DirectorySearchRepository(RestHighLevelClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
        if (properties.getDirectoryIndexAlias().equals(properties.getTicketIndexAlias())) {
            throw new IllegalArgumentException("目录别名不能与工单别名相同");
        }
    }

    /**
     * 确认别名仍存在，避免缓存快照阻止被外部删除的索引重新回填。
     *
     * @return 目录别名仍指向已发布快照时为 true
     * @throws IOException 处理过程中发生IO异常时
     */
    public boolean hasPublishedSnapshot() throws IOException {
        return !findPublishedIndices().isEmpty();
    }

    /**
     * 创建新快照、完整写入并刷新，再一次性切换别名；失败不会将半成品作为查询入口。
     *
     * @param documents Directory搜索文档列表，对应 documents
     * @throws IOException 处理过程中发生IO异常时
     */
    public void publishSnapshot(List<DirectorySearchDocument> documents) throws IOException {
        // 找出当前已发布快照的索引
        Set<String> previous = findPublishedIndices();
        // 生成新快照的索引名称
        String index = properties.getDirectoryIndexPrefix() + "-" + UUID.randomUUID();
        // 标记新索引已创建
        boolean created = false;
        // 标记别名切换已尝试
        boolean publicationAttempted = false;
        try {
            // 创建新索引
            createIndex(index);
            created = true;
            writeDocuments(index, documents);
            if (client.indices().refresh(new RefreshRequest(index), RequestOptions.DEFAULT).getFailedShards() > 0) {
                throw new IOException("用户目录索引刷新失败");
            }
            IndicesAliasesRequest aliases = new IndicesAliasesRequest();
            for (String oldIndex : previous) {
                aliases.addAliasAction(IndicesAliasesRequest.AliasActions.remove()
                        .index(oldIndex).alias(properties.getDirectoryIndexAlias()));
            }
            aliases.addAliasAction(IndicesAliasesRequest.AliasActions.add()
                    .index(index).alias(properties.getDirectoryIndexAlias()).writeIndex(true));
            publicationAttempted = true;
            if (!client.indices().updateAliases(aliases, RequestOptions.DEFAULT).isAcknowledged()) {
                throw new IOException("用户目录别名切换未确认");
            }
            previous.forEach(old -> retiredIndices.put(old, System.currentTimeMillis() + RETIRE_DELAY_MILLIS));
        } catch (IOException | RuntimeException failure) {
            if (created) {
                // 别名请求超时时可能已切换成功；稍后确认没有任何别名再清理。
                retiredIndices.put(index, System.currentTimeMillis() + RETIRE_DELAY_MILLIS);
                if (!publicationAttempted) {
                    try {
                        deleteUnpublishedIndex(index);
                        retiredIndices.remove(index);
                    } catch (IOException | RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw failure;
        }
    }

    /**
     * 校验目录别名只指向本功能生成的物理索引，配置错指向其他业务时拒绝切换。
     *
     * @return 目录读别名当前指向的受管物理快照索引集合
     * @throws IOException 处理过程中发生IO异常时
     */
    private Set<String> findPublishedIndices() throws IOException {
        GetAliasesResponse response = client.indices().getAlias(
                new GetAliasesRequest(properties.getDirectoryIndexAlias()), RequestOptions.DEFAULT);
        if (response.status() == RestStatus.NOT_FOUND) {
            return Collections.emptySet();
        }

        Set<String> indices = response.getAliases().keySet();
        if (response.status() != RestStatus.OK || indices.size() != 1
                || indices.stream().anyMatch(index -> !isManagedIndex(index))) {
            throw new IOException("用户目录别名必须指向一个本功能生成的索引");
        }
        return new HashSet<>(indices);
    }

    /**
     * 只管理配置前缀加 UUID 的目录索引，不按宽泛通配符执行删除。
     *
     * @param index 索引
     * @return 是否满足校验条件
     */
    private boolean isManagedIndex(String index) {
        String prefix = properties.getDirectoryIndexPrefix() + "-";
        if (!index.startsWith(prefix)) {
            return false;
        }
        try {
            return UUID.fromString(index.substring(prefix.length())).toString()
                    .equals(index.substring(prefix.length()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 新索引使用固定字段映射及现有分片配置，禁止自动猜测姓名和联系方式类型。
     *
     * @param index 索引
     * @throws IOException 处理过程中发生IO异常时
     */
    private void createIndex(String index) throws IOException {
        String mapping;
        try (InputStream input = new ClassPathResource("elasticsearch/directory-index-v1.json").getInputStream()) {
            mapping = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
        CreateIndexRequest request = new CreateIndexRequest(index)
                .settings(Settings.builder().put("index.number_of_shards", properties.getNumberOfShards())
                        .put("index.number_of_replicas", properties.getNumberOfReplicas()))
                .mapping(mapping, XContentType.JSON);
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        if (!response.isAcknowledged() || !response.isShardsAcknowledged()) {
            throw new IOException("用户目录索引创建未确认");
        }
    }

    /**
     * 分批写入完整投影，每一项都成功后才允许发布，密码及动态统计字段不会进入 ES。
     *
     * @param index 索引
     * @param documents Directory搜索文档列表，对应 documents
     * @throws IOException 处理过程中发生IO异常时
     */
    private void writeDocuments(String index, List<DirectorySearchDocument> documents) throws IOException {
        int batchSize = properties.getMaxBulkSize();
        long deadline = System.nanoTime() + SEARCH_TIMEOUT_NANOS;
        if (batchSize < 1) {
            throw new IllegalArgumentException("目录写入批大小必须大于零");
        }
        for (int from = 0; from < documents.size(); from += batchSize) {
            if (System.nanoTime() > deadline) {
                throw new IOException("用户目录快照写入超时");
            }
            BulkRequest request = new BulkRequest();
            for (DirectorySearchDocument document : documents.subList(from, Math.min(from + batchSize, documents.size()))) {
                if (document.getUserId() == null || document.getUserId() <= 0) {
                    throw new IllegalArgumentException("目录用户 ID 无效");
                }
                // 添加文档到批量请求
                request.add(new IndexRequest(index)
                        .id(document.getUserId().toString())
                        .source(toSource(document)));
            }
            BulkResponse response = client.bulk(request, RequestOptions.DEFAULT);
            if (response.hasFailures() || response.getItems().length != request.numberOfActions()) {
                throw new IOException("用户目录批量写入未全部成功");
            }
            for (org.elasticsearch.action.bulk.BulkItemResponse item : response.getItems()) {
                if (item.getResponse().getShardInfo().getFailed() > 0
                        || item.getResponse().getShardInfo().getSuccessful() == 0) {
                    throw new IOException("用户目录分片写入未全部成功");
                }
            }
        }
    }

    /**
     * 统一折叠大小写及常见组合重音，保留中文子串；技能独立存为数组以免跨技能拼接误命中。
     *
     * @param document 仅含目录搜索字段的完整用户投影
     * @return 经过统一大小写与重音折叠的索引字段映射，技能保持独立数组
     */
    private Map<String, Object> toSource(DirectorySearchDocument document) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("username", normalize(document.getUsername()));
        source.put("realName", normalize(document.getRealName()));
        source.put("email", normalize(document.getEmail()));
        source.put("phone", normalize(document.getPhone()));
        source.put("skillNames", document.getSkillNames().stream().filter(value -> value != null)
                .map(this::normalize).distinct().collect(Collectors.toList()));
        return source;
    }

    /**
     * 文档和查询使用同样的大小写、重音折叠规则，避免 ES 的 ASCII 大小写开关限制中文目录。
     *
     * @param value 待统一大小写和重音的目录字段或关键词，可为 null
     * @return 按 Locale.ROOT 小写并去除组合重音的文本；输入为 null 时为 null
     */
    private String normalize(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 将原 LIKE 的 %、_、反斜杠转为 ES 模式；* 和 ? 在原 SQL 中是普通字符，必须转义。
     *
     * @param keyword 查询关键词
     * @return 保留原 SQL LIKE 的 %、_ 语义并转义 ES 特有通配符的包含匹配模式
     */
    private String toWildcardPattern(String keyword) {
        String like = "%" + normalize(keyword) + "%";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < like.length(); index++) {
            char character = like.charAt(index);
            if (character == '\\' && index + 1 < like.length()) {
                appendLiteral(result, like.charAt(++index));
            } else if (character == '%') {
                result.append('*');
            } else if (character == '_') {
                result.append('?');
            } else {
                appendLiteral(result, character);
            }
        }
        return result.toString();
    }

    /**
     * 转义 ES 自身的通配符，防止普通用户名中的星号、问号被扩大匹配。
     *
     * @param pattern 正在组装的 ES 通配符模式
     * @param character 按字面意义追加、必要时转义的字符
     */
    private void appendLiteral(StringBuilder pattern, char character) {
        if (character == '*' || character == '?' || character == '\\') {
            pattern.append('\\');
        }
        pattern.append(character);
    }

    /**
     * 使用滚动快照取全量候选 ID，不受 10000 条窗口限制，不提前截断导致 SQL 分页总数错误。
     *
     * @param keyword 查询关键词
     * @param handlers true 匹配姓名及技能，false 匹配姓名、邮箱及电话
     * @return 完整的候选用户 ID 列表；角色、状态、排序和分页由后续 MySQL 业务查询处理
     * @throws IOException 处理过程中发生IO异常时
     */
    public List<Long> searchIds(String keyword, boolean handlers) throws IOException {
        String[] fields = handlers ? new String[]{"username", "realName", "skillNames"}
                : new String[]{"username", "realName", "email", "phone"};
        BoolQueryBuilder query = QueryBuilders.boolQuery().minimumShouldMatch(1);
        for (String field : fields) {
            query.should(QueryBuilders.wildcardQuery(field, toWildcardPattern(keyword)));
        }
        SearchRequest request = new SearchRequest(properties.getDirectoryIndexAlias())
                .allowPartialSearchResults(false).scroll(SCROLL_KEEP_ALIVE)
                .source(new SearchSourceBuilder().query(query).fetchSource(false).trackTotalHits(true)
                        .sort("_doc", SortOrder.ASC).size(1000));
        List<Long> ids = new ArrayList<>();
        String scrollId = null;
        long deadline = System.nanoTime() + SEARCH_TIMEOUT_NANOS;
        Exception searchFailure = null;
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            scrollId = response.getScrollId();
            validateSearchResponse(response);
            long total = response.getHits().getTotalHits().value;
            while (true) {
                scrollId = response.getScrollId();
                validateSearchResponse(response);
                if (System.nanoTime() > deadline) {
                    throw new IOException("用户目录搜索未完整完成");
                }
                for (SearchHit hit : response.getHits()) {
                    long id = Long.parseLong(hit.getId());
                    if (id <= 0) {
                        throw new IOException("用户目录索引包含无效 ID");
                    }
                    ids.add(id);
                }
                if (response.getHits().getHits().length == 0 || ids.size() >= total) {
                    if (ids.size() != total || new HashSet<>(ids).size() != ids.size()) {
                        throw new IOException("用户目录搜索结果不完整或重复");
                    }
                    return ids;
                }
                response = client.scroll(new SearchScrollRequest(scrollId).scroll(SCROLL_KEEP_ALIVE), RequestOptions.DEFAULT);
            }
        } catch (IOException | RuntimeException failure) {
            searchFailure = failure;
            throw failure;
        } finally {
            if (scrollId != null) {
                ClearScrollRequest clear = new ClearScrollRequest();
                clear.addScrollId(scrollId);
                try {
                    if (!client.clearScroll(clear, RequestOptions.DEFAULT).isSucceeded()) {
                        throw new IOException("用户目录搜索游标清理未确认");
                    }
                } catch (IOException | RuntimeException cleanupFailure) {
                    if (searchFailure == null) {
                        throw cleanupFailure;
                    }
                    searchFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    /**
     * 只接受准确总数且所有分片完成的响应，不能将部分搜索结果作为完整 SQL 候选集合。
     *
     * @param response 搜索响应
     * @throws IOException 处理过程中发生IO异常时
     */
    private void validateSearchResponse(SearchResponse response) throws IOException {
        if (response.isTimedOut() || response.getFailedShards() > 0
                || response.getHits() == null || response.getHits().getTotalHits() == null
                || response.getHits().getTotalHits().relation != org.apache.lucene.search.TotalHits.Relation.EQUAL_TO) {
            throw new IOException("用户目录搜索未完整完成");
        }
    }

    /**
     * 发现重启遗留的未挂别名快照，至少再保留十分钟；仅清理带本功能标记的明确索引。
     *
     * @throws IOException 处理过程中发生IO异常时
     */
    public void cleanupRetiredIndices() throws IOException {
        GetIndexResponse metadata = client.indices().get(
                new GetIndexRequest(properties.getDirectoryIndexPrefix() + "-*")
                        .indicesOptions(IndicesOptions.lenientExpandOpen()), RequestOptions.DEFAULT);
        for (String index : metadata.getIndices()) {
            Object owner = metadata.getMappings().get(index).sourceAsMap().get("_meta");
            boolean managed = isManagedIndex(index) && owner instanceof Map
                    && "work-order-directory-v1".equals(((Map<?, ?>) owner).get("owner"));
            if (managed && metadata.getAliases().getOrDefault(index, Collections.emptyList()).isEmpty()) {
                retiredIndices.putIfAbsent(index, System.currentTimeMillis() + RETIRE_DELAY_MILLIS);
            }
        }
        for (String index : new ArrayList<>(retiredIndices.keySet())) {
            if (retiredIndices.get(index) > System.currentTimeMillis()) {
                continue;
            }
            GetAliasesResponse aliases;
            try {
                aliases = client.indices().getAlias(new GetAliasesRequest().indices(index), RequestOptions.DEFAULT);
            } catch (ElasticsearchStatusException exception) {
                if (exception.status() == RestStatus.NOT_FOUND) {
                    retiredIndices.remove(index);
                    continue;
                }
                throw exception;
            }
            boolean hasAlias = aliases.getAliases().values().stream().anyMatch(list -> !list.isEmpty());
            if (!hasAlias && isManagedIndex(index)) {
                deleteUnpublishedIndex(index);
                retiredIndices.remove(index);
            }
        }
    }

    /**
     * 删除本次生成但未发布的索引；创建失败产生的 404 可忽略。
     *
     * @param index 索引
     * @throws IOException 处理过程中发生IO异常时
     */
    private void deleteUnpublishedIndex(String index) throws IOException {
        try {
            if (!client.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT).isAcknowledged()) {
                throw new IOException("用户目录旧索引删除未确认");
            }
        } catch (ElasticsearchStatusException exception) {
            if (exception.status() != RestStatus.NOT_FOUND) {
                throw exception;
            }
        }
    }
}
