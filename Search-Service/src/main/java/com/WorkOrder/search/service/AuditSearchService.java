package com.WorkOrder.search.service;

import com.WorkOrder.dashboard.dto.AuditQuery;
import com.WorkOrder.dashboard.dto.ConfigurationAuditItem;
import com.WorkOrder.dashboard.dto.TicketAuditItem;
import com.WorkOrder.dashboard.mapper.AuditMapper;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.search.model.AuditSearchDocument;
import com.WorkOrder.search.repository.AuditSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员审计检索：当前动态字段先转换为 ID 过滤，ES 分页后读取数据库响应。
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class AuditSearchService {
    /**
     * 查询当前姓名、编号和 ES 命中的源日志记录。
     */
    private final AuditMapper mapper;

    /**
     * 在全部过滤和稳定排序完成后执行 ES 分页。
     */
    private final AuditSearchRepository repository;

    /**
     * 判断首轮回填及故障恢复是否已经完成。
     */
    private final AuditSearchSynchronizer synchronizer;

    /**
     * 在 ES 分页前应用当前姓名和工单编号筛选，回表后恢复 ES 的稳定次序。
     *
     * @param source 待转换或读取的源数据
     * @return 工单审计条目的分页结果
     * @throws IOException 处理过程中发生IO异常时
     */
    public PageResult<TicketAuditItem> tickets(AuditQuery source) throws IOException {
        AuditQuery query = source.normalizedCopy();
        // 首轮回填/故障恢复未完成时拒绝搜索，避免将不完整索引当作空结果。
        requireIndexReady();

        // null 表示未设置该筛选；空 ID 列表表示数据库中没有符合当前条件的记录。
        List<Long> operatorIds = query.getOperator() == null
                ? null : mapper.selectTicketOperatorIds(query);
        List<Long> ticketIds = query.getTicketNo() == null
                ? null : mapper.selectTicketIds(query);
        PageResult<AuditSearchDocument> searchPage = repository.search(false, query, operatorIds, ticketIds);
        List<AuditSearchDocument> documents = searchPage.getList();
        // 校验工单日志 ID
        documents.forEach(this::validateTicketDocumentId);

        if (documents.isEmpty()) {
            return new PageResult<>(
                    Collections.emptyList(),
                    searchPage.getTotal(),
                    query.getPage(),
                    query.getPageSize());
        }

        // 按 ES 当前页顺序回表
        List<TicketAuditItem> currentItems = loadTicketItemsInSearchOrder(query, documents);
        return new PageResult<>(currentItems, searchPage.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 配置关键词搜索保留原始 JSON 和版本字符串，操作人姓名取当前数据库值。
     *
     * @param source 待转换或读取的源数据
     * @return 配置审计条目的分页结果
     * @throws IOException 处理过程中发生IO异常时
     */
    public PageResult<ConfigurationAuditItem> configurations(AuditQuery source) throws IOException {
        AuditQuery query = source.normalizedCopy();
        requireIndexReady();

        // 配置键继续由数据库比较，避免 ES 精确匹配与原 SQL 的比较规则不一致。
        List<Long> operatorIds = query.getOperator() == null
                ? null : mapper.selectConfigurationOperatorIds(query);
        List<Long> configurationIds = query.getConfigKey() == null || query.getConfigKey().isEmpty()
                ? null : mapper.selectConfigurationIds(query);
        PageResult<AuditSearchDocument> searchPage = repository.search(true, query, operatorIds, configurationIds);
        List<AuditSearchDocument> documents = searchPage.getList();
        documents.forEach(this::validateConfigurationDocumentId);

        if (documents.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), searchPage.getTotal(),
                    query.getPage(), query.getPageSize());
        }

        List<ConfigurationAuditItem> currentItems = loadConfigurationItemsInSearchOrder(query, documents);
        return new PageResult<>(currentItems, searchPage.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 按来源批量回表，再按 ES 当前页逐条取值；已经删除或不再符合条件的日志不返回。
     *
     * @param query 审计查询条件
     * @param documents 审计搜索文档列表，对应 documents
     * @return 工单审计条目列表
     */
    private List<TicketAuditItem> loadTicketItemsInSearchOrder(
            AuditQuery query, List<AuditSearchDocument> documents) {

        List<Long> operationIds = collectSourceIds(documents, "OPERATION");
        List<Long> statusIds = collectSourceIds(documents, "STATUS");

        List<TicketAuditItem> databaseItems = mapper.selectTicketsByIds(query, operationIds, statusIds);

        Map<String, TicketAuditItem> currentItemsById = databaseItems.stream()
                .collect(Collectors.toMap(TicketAuditItem::getId, item -> item,
                        (existingItem, duplicateItem) -> existingItem));

        // 按 ES 当前页顺序组装结果
        List<TicketAuditItem> orderedItems = new ArrayList<>();
        for (AuditSearchDocument document : documents) {
            TicketAuditItem currentItem = currentItemsById.get(document.getId());
            if (currentItem != null) {
                orderedItems.add(currentItem);
            }
        }
        return orderedItems;
    }

    /**
     * 一次读取配置日志的当前字段，再按 ES 当前页顺序组装，保留原始 JSON 和版本。
     *
     * @param query 审计查询条件
     * @param documents 审计搜索文档列表，对应 documents
     * @return 配置审计条目列表
     */
    private List<ConfigurationAuditItem> loadConfigurationItemsInSearchOrder(
            AuditQuery query, List<AuditSearchDocument> documents) {
        List<Long> configurationIds = collectSourceIds(documents, "CONFIGURATION");
        List<ConfigurationAuditItem> databaseItems = mapper.selectConfigurationsByIds(query, configurationIds);
        Map<Long, ConfigurationAuditItem> currentItemsById = databaseItems.stream()
                .collect(Collectors.toMap(ConfigurationAuditItem::getId, item -> item,
                        (existingItem, duplicateItem) -> existingItem));

        List<ConfigurationAuditItem> orderedItems = new ArrayList<>();
        for (AuditSearchDocument document : documents) {
            ConfigurationAuditItem currentItem = currentItemsById.get(document.getSourceId());
            if (currentItem != null) {
                orderedItems.add(currentItem);
            }
        }
        return orderedItems;
    }

    /**
     * 提取指定来源的数值主键，保持 ES 顺序，不把字符串 ID 用于数值排序。
     *
     * @param documents 审计搜索文档列表，对应 documents
     * @param source 待转换或读取的源数据
     * @return 长整型数值列表
     */
    private List<Long> collectSourceIds(List<AuditSearchDocument> documents, String source) {
        return documents.stream()
                .filter(document -> source.equals(document.getSource()))
                .map(AuditSearchDocument::getSourceId)
                .collect(Collectors.toList());
    }

    /**
     * 首轮回填或故障恢复未完成时拒绝搜索，避免将不完整索引当作空结果。
     */
    private void requireIndexReady() {
        if (!synchronizer.isReady()) {
            throw new IllegalStateException("审计 ES 索引尚未完成同步，请稍后重试");
        }
    }

    /**
     * 校验跨表工单日志 ID，避免非法搜索响应被用于数据库回表。
     *
     * @param document 文档
     */
    private void validateTicketDocumentId(AuditSearchDocument document) {
        if (!hasConsistentDocumentId(document)) {
            throw new IllegalStateException("ES 返回了非法工单审计 ID");
        }

        boolean ticketKind = "TICKET".equals(document.getKind());
        boolean operationSource = "OPERATION".equals(document.getSource());
        boolean statusSource = "STATUS".equals(document.getSource());
        boolean ticketSource = operationSource || statusSource;
        if (!ticketKind || !ticketSource) {
            throw new IllegalStateException("ES 返回了非法工单审计 ID");
        }
    }

    /**
     * 校验配置日志类型和数值 ID，防止其他来源混入配置回表。
     *
     * @param document 文档
     */
    private void validateConfigurationDocumentId(AuditSearchDocument document) {
        if (!hasConsistentDocumentId(document)) {
            throw new IllegalStateException("ES 返回了非法配置审计 ID");
        }

        boolean configurationKind = "CONFIGURATION".equals(document.getKind());
        boolean configurationSource = "CONFIGURATION".equals(document.getSource());
        if (!configurationKind || !configurationSource) {
            throw new IllegalStateException("ES 返回了非法配置审计 ID");
        }
    }

    /**
     * 文档 ID 必须与来源及正数主键一致；系统操作人 ID 不参与该校验。
     *
     * @param document 文档
     * @return 是否满足校验条件
     */
    private boolean hasConsistentDocumentId(AuditSearchDocument document) {
        if (document == null || document.getSourceId() == null || document.getSourceId() <= 0) {
            return false;
        }
        String expectedId = document.getSource() + ":" + document.getSourceId();
        return expectedId.equals(document.getId());
    }
}
