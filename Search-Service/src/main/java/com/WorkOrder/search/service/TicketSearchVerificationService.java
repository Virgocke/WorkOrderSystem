package com.WorkOrder.search.service;

import com.WorkOrder.search.model.*;
import com.WorkOrder.search.repository.TicketSearchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** 历史验证与周期对账共用：实时读版本，缺失或落后才补写完整源行。 */
@Service
@ConditionalOnProperty(prefix="work-order.elasticsearch.ticket-sync", name="enabled", havingValue="true")
public class TicketSearchVerificationService {
    private final TicketSearchRepository repository;
    private final TicketProjectionService projection;
    private final TicketSearchIndexService indexes;

    /** 注入统一目标校验、版本读写与投影转换通路。 */
    public TicketSearchVerificationService(TicketSearchRepository repository, TicketProjectionService projection,
                                           TicketSearchIndexService indexes) {
        this.repository = repository; this.projection = projection; this.indexes = indexes;
    }

    /** 一批使用固定目标；失败抛出，调用方不得提交本批游标。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void verifyAndRepair(TicketSearchWriteTarget target, List<TicketIndexSource> sources) throws IOException {
        indexes.validateWriteTarget(target);
        List<Long> ids = sources.stream().map(TicketIndexSource::getTicketId).collect(Collectors.toList());
        // 实时读取文档版本，不依赖搜索 refresh 的可见性来判断工单是否已写入。
        Map<Long, TicketSearchStoredVersion> stored = repository.multiGetVersions(target, ids);
        List<TicketIndexSource> repairs = new ArrayList<>();
        for (TicketIndexSource source : sources) {
            if (source.getSourceVersion() == null || source.getSourceVersion() <= 0) {
                throw new IOException("SOURCE_VERSION_INVALID");
            }
            TicketSearchStoredVersion version = stored.get(source.getTicketId());
            if (version == null) { throw new IOException("MGET_ITEM_MISSING"); }
            // ES 版本元数据与正文 sourceVersion 必须一致，异常文档不能当作已覆盖而跳过。
            if (version.isFound() && (version.getSourceVersion() == null || version.getVersion() <= 0
                    || version.getVersion() != version.getSourceVersion())) {
                throw new IOException("STORED_VERSION_INCONSISTENT");
            }
            // 只补缺失或落后的工单；并行增量已经写入更高版本时保留它的结果。
            if (!version.isFound() || version.getVersion() < source.getSourceVersion()) {
                repairs.add(source);
            }
        }
        if (!repairs.isEmpty()) {
            // Bulk 请求成功不代表每项都成功，整批完成后才允许调用方提交游标。
            requireComplete(projection.writeBatch(target, repairs), repairs.size());
        }
    }

    /** 每项都完成或被同版本及更高版本覆盖，整批才算成功。 */
    public static void requireComplete(TicketSearchBulkResult result, int expected) throws IOException {
        if (result == null || result.hasFailures() || result.getCompletedCount() != expected) {
            throw new IOException("BULK_INCOMPLETE");
        }
    }
}
