package com.WorkOrder.search.repository;

import com.WorkOrder.dashboard.dto.AuditQuery;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.search.model.AuditSearchDocument;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.WorkOrder.search.repository.AuditSearchRepository.CONFIGURATION_KIND;
import static com.WorkOrder.search.repository.AuditSearchRepository.OPERATION_SOURCE;
import static com.WorkOrder.search.repository.AuditSearchRepository.STATUS_SOURCE;
import static com.WorkOrder.search.repository.AuditSearchRepository.TICKET_KIND;

/** 执行审计分页并解析稳定来源标识，负责响应完整性和滚动游标的释放。 */
final class AuditSearchPageReader {

    /** ES 默认分页窗口，超过窗口时通过固定快照读取。 */
    private static final int RESULT_WINDOW_SIZE = 10000;

    /** 深分页逐批读取，避免把所有跳过的记录存入内存。 */
    private static final int SCROLL_BATCH_SIZE = 1000;

    private static final TimeValue SCROLL_KEEP_ALIVE = TimeValue.timeValueMinutes(1);

    private final RestHighLevelClient client;

    /** 复用仓库客户端，不创建或关闭连接。 */
    AuditSearchPageReader(RestHighLevelClient client) {
        this.client = client;
    }

    /** 在默认窗口内使用 from/size，超过窗口则用 scroll 保留任意有效页码。 */
    PageResult<AuditSearchDocument> readPage(String indexAlias, SearchSourceBuilder searchSource, AuditQuery query)
            throws IOException {
        long offset = query.getOffset();
        if (offset + query.getPageSize() > RESULT_WINDOW_SIZE) {
            return readPageWithScroll(indexAlias, searchSource, query);
        }

        searchSource.from((int) offset).size(query.getPageSize());
        SearchRequest request = new SearchRequest(indexAlias)
                .source(searchSource)
                .allowPartialSearchResults(false);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        long total = requireExactTotal(response);
        return new PageResult<>(readIdentifiers(response), total, query.getPage(), query.getPageSize());
    }

    /** 用同一快照跳过前面的命中，只收集目标页，并在成功或失败后释放最后一个游标。 */
    private PageResult<AuditSearchDocument> readPageWithScroll(String indexAlias, SearchSourceBuilder searchSource,
                                                             AuditQuery query) throws IOException {
        searchSource.from(0).size(SCROLL_BATCH_SIZE);
        String scrollId = null;
        Throwable searchFailure = null;

        try {
            SearchRequest request = new SearchRequest(indexAlias)
                    .source(searchSource)
                    .scroll(SCROLL_KEEP_ALIVE)
                    .allowPartialSearchResults(false);
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            scrollId = response.getScrollId();
            long total = requireExactTotal(response);
            long offset = query.getOffset();
            if (offset >= total) {
                return new PageResult<>(Collections.emptyList(), total, query.getPage(), query.getPageSize());
            }

            List<AuditSearchDocument> pageRecords = new ArrayList<>();
            long visitedCount = 0;
            while (true) {
                requireExactTotal(response);
                SearchHit[] hits = response.getHits().getHits();
                if (hits.length == 0) {
                    throw new IOException("审计深分页提前结束，不能返回不完整的结果");
                }
                for (SearchHit hit : hits) {
                    boolean belongsToRequestedPage = visitedCount >= offset;
                    visitedCount++;
                    if (!belongsToRequestedPage) {
                        continue;
                    }
                    pageRecords.add(readIdentifier(hit));
                    if (pageRecords.size() == query.getPageSize() || visitedCount >= total) {
                        return new PageResult<>(pageRecords, total, query.getPage(), query.getPageSize());
                    }
                }

                response = readNextScrollBatch(scrollId);
                String nextScrollId = response.getScrollId();
                if (!StringUtils.hasText(nextScrollId)) {
                    throw new IOException("审计深分页响应缺少后续游标");
                }
                scrollId = nextScrollId;
            }
        } catch (IOException | RuntimeException exception) {
            searchFailure = exception;
            throw exception;
        } finally {
            clearScrollAfterSearch(scrollId, searchFailure);
        }
    }

    /** 读取下一批快照命中，当前响应缺少游标时明确失败。 */
    private SearchResponse readNextScrollBatch(String scrollId) throws IOException {
        if (!StringUtils.hasText(scrollId)) {
            throw new IOException("审计深分页响应缺少游标");
        }
        SearchScrollRequest request = new SearchScrollRequest(scrollId).scroll(SCROLL_KEEP_ALIVE);
        return client.scroll(request, RequestOptions.DEFAULT);
    }

    /** 清理失败不能掩盖原搜索错误；搜索成功时则直接报告清理失败。 */
    private void clearScrollAfterSearch(String scrollId, Throwable searchFailure) throws IOException {
        if (!StringUtils.hasText(scrollId)) {
            return;
        }
        try {
            ClearScrollRequest request = new ClearScrollRequest();
            request.addScrollId(scrollId);
            if (!client.clearScroll(request, RequestOptions.DEFAULT).isSucceeded()) {
                throw new IOException("审计搜索游标清理失败");
            }
        } catch (IOException | RuntimeException cleanupFailure) {
            if (searchFailure == null) {
                throw cleanupFailure;
            }
            searchFailure.addSuppressed(cleanupFailure);
        }
    }

    /** 拒绝超时、分片失败和截断总数，避免把不完整结果当作正常分页。 */
    private long requireExactTotal(SearchResponse response) throws IOException {
        if (response.isTimedOut() || response.getFailedShards() > 0) {
            throw new IOException("审计 Elasticsearch 搜索超时或分片失败");
        }
        TotalHits total = response.getHits().getTotalHits();
        if (total == null || total.relation != TotalHits.Relation.EQUAL_TO) {
            throw new IOException("审计 Elasticsearch 未返回准确总数");
        }
        return total.value;
    }

    /** 保留 ES 命中顺序，仅返回回表需要的来源元数据。 */
    private List<AuditSearchDocument> readIdentifiers(SearchResponse response) throws IOException {
        List<AuditSearchDocument> identifiers = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            identifiers.add(readIdentifier(hit));
        }
        return identifiers;
    }

    /** 校验数值主键、文档 ID 与日志来源的一致性，不返回索引中的日志正文。 */
    private AuditSearchDocument readIdentifier(SearchHit hit) throws IOException {
        Map<String, Object> fields = hit.getSourceAsMap();
        if (fields == null || !(fields.get("sourceId") instanceof Number)
                || !(fields.get("source") instanceof String) || !(fields.get("kind") instanceof String)) {
            throw new IOException("审计 Elasticsearch 命中缺少稳定来源标识");
        }

        String source = (String) fields.get("source");
        String kind = (String) fields.get("kind");
        long sourceId = readSourceId(fields.get("sourceId"));
        String documentId = source + ":" + sourceId;
        boolean idMatches = documentId.equals(hit.getId()) && documentId.equals(fields.get("id"));
        if (sourceId <= 0 || !idMatches || !isValidSource(kind, source)) {
            throw new IOException("审计 Elasticsearch 命中的来源标识无效");
        }

        AuditSearchDocument identifier = new AuditSearchDocument();
        identifier.setId(documentId);
        identifier.setSource(source);
        identifier.setKind(kind);
        identifier.setSourceId(sourceId);
        return identifier;
    }

    /** 直接解析整数文本，避免雪花 ID 经 double 转换后丢失精度。 */
    private long readSourceId(Object value) throws IOException {
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new IOException("审计 Elasticsearch 来源 ID 不是有效整数");
        }
    }

    /** 工单只允许操作/状态日志，配置审计只允许配置日志。 */
    private boolean isValidSource(String kind, String source) {
        if (TICKET_KIND.equals(kind)) {
            return OPERATION_SOURCE.equals(source) || STATUS_SOURCE.equals(source);
        }
        return CONFIGURATION_KIND.equals(kind) && CONFIGURATION_KIND.equals(source);
    }
}
