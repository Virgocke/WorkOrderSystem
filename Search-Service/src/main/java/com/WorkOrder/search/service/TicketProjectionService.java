package com.WorkOrder.search.service;

import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.search.mapper.TicketIndexSourceMapper;
import com.WorkOrder.search.model.TicketIndexSource;
import com.WorkOrder.search.model.TicketSearchBulkResult;
import com.WorkOrder.search.model.TicketSearchWriteOutcome;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import com.WorkOrder.search.repository.TicketSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 消息、后续导入和补偿共用的投影通路，版本仲裁由 ES 原子完成。
 */
public class TicketProjectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketProjectionService.class);

    private final TicketIndexSourceMapper sourceMapper;
    private final TicketSearchDocumentConverter converter;
    private final TicketSearchWriteTargetResolver targetResolver;
    private final TicketSearchIndexService indexService;
    private final TicketSearchRepository repository;

    /**
     * 保存短读、统一转换、目标校验及版本化写入依赖，不在构造时连接外部服务。
     *
     * @param sourceMapper 工单索引源数据数据访问器
     * @param converter 转换器
     * @param targetResolver 目标Resolver
     * @param indexService 工单搜索索引服务
     * @param repository 工单搜索仓储
     */
    public TicketProjectionService(TicketIndexSourceMapper sourceMapper,
                                   TicketSearchDocumentConverter converter,
                                   TicketSearchWriteTargetResolver targetResolver,
                                   TicketSearchIndexService indexService,
                                   TicketSearchRepository repository) {
        Assert.notNull(sourceMapper, "工单搜索源查询不能为空");
        Assert.notNull(converter, "工单搜索转换器不能为空");
        Assert.notNull(targetResolver, "工单搜索目标解析器不能为空");
        Assert.notNull(indexService, "工单搜索索引管理器不能为空");
        Assert.notNull(repository, "工单搜索仓库不能为空");
        this.sourceMapper = sourceMapper;
        this.converter = converter;
        this.targetResolver = targetResolver;
        this.indexService = indexService;
        this.repository = repository;
    }

    /**
     * 同步事件要求的版本或当前更高版本；文档和写入版本来自同一源行。
     * 暂停调用方事务，目标短读返回后才调用 ES，异常保留给消息重试。
     *
     * @param ticketId 待回源工单的正整数主键
     * @param requestedVersion 消息要求的正整数最低源版本
     * @return 本次写入成功或目标已有相同及更高版本
     * @throws IOException 目标校验、源版本检查或 ES 写入失败
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TicketSearchWriteOutcome synchronize(Long ticketId, Long requestedVersion) throws IOException {
        Assert.isTrue(ticketId != null && ticketId > 0, "ticketId 必须为正数");
        Assert.isTrue(requestedVersion != null && requestedVersion > 0, "requestedVersion 必须为正数");
        // 一次同步只固定一个任务代次，途中即使开始重建，也不能把同次操作写到另一个任务。
        TicketSearchWriteTarget target = targetResolver.resolve();
        Long sourceVersion = null;
        try {
            indexService.validateWriteTarget(target);
            // 消息只携带工单 ID 和最低版本；完整字段与当前版本必须从同一条 MySQL 源行取得。
            TicketIndexSource source = sourceMapper.selectById(ticketId);
            if (source == null || !ticketId.equals(source.getTicketId())) {
                throw new IOException("工单搜索源行不存在或标识不一致，ticketId=" + ticketId
                        + ", generation=" + target.getGeneration());
            }
            sourceVersion = source.getSourceVersion();
            // 当前源版本可以高于消息版本；若反而落后，不能确认消费，必须留给消息重试。
            if (sourceVersion == null || sourceVersion <= 0 || sourceVersion < requestedVersion) {
                throw new IOException("工单搜索源版本无效或落后，ticketId=" + ticketId
                        + ", generation=" + target.getGeneration() + ", requestedVersion=" + requestedVersion
                        + ", sourceVersion=" + sourceVersion);
            }
            TicketSearchDocument document = converter.convert(source);
            TicketSearchWriteOutcome outcome = repository.save(target, document);
            LOGGER.debug("工单投影已覆盖，ticketId={}，generation={}，sourceVersion={}，outcome={}",
                    ticketId, target.getGeneration(), sourceVersion, outcome);
            return outcome;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("工单投影同步失败，ticketId={}，generation={}，requestedVersion={}，sourceVersion={}，failureType={}",
                    ticketId, target.getGeneration(), requestedVersion, sourceVersion,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    /**
     * 将一个短读批次写入调用方已固定的任务代次，不重新解析目标或逐行重新回源。
     * 全部源行先通过同一转换器校验，再校验索引并发送统一 external 版本 Bulk。
     * 暂停调用方数据库事务，部分失败及网络结果不确定继续交给任务保留游标处理。
     *
     * @param target 从共享任务记录取得的不可变写目标
     * @param sources 同一批次 SQL 取得的完整源行，字段与版本来自同次读取
     * @return 整批已写入或已覆盖的分类结果
     * @throws IOException 索引不兼容、网络错误或 Bulk 存在真正失败项
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TicketSearchBulkResult writeBatch(TicketSearchWriteTarget target, List<TicketIndexSource> sources)
            throws IOException {
        Assert.notNull(target, "工单搜索批次写目标不能为空");
        Assert.notNull(sources, "工单搜索源批次不能为空");
        List<TicketSearchDocument> documents = new ArrayList<>(sources.size());
        // 先校验整批源行再发送，避免前几条已经写入后才发现后续源数据不合法。
        for (TicketIndexSource source : sources) {
            documents.add(converter.convert(source));
        }
        indexService.validateWriteTarget(target);
        // 复用任务传入的固定目标；真正的乱序保护由每项写入携带的源版本完成。
        return repository.bulkSave(target, documents);
    }
}
