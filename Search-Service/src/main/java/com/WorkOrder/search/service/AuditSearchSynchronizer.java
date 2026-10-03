package com.WorkOrder.search.service;

import com.WorkOrder.search.config.AuditSearchProperties;
import com.WorkOrder.search.mapper.AuditIndexSourceMapper;
import com.WorkOrder.search.model.AuditSearchDocument;
import com.WorkOrder.search.repository.AuditSearchRepository;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;

/**
 * 分批轮扫已提交的审计日志并幂等写入 ES，不在业务写事务中访问搜索服务。
 * 每轮结束都会从主键零重新扫描：ID 的分配顺序不等于提交顺序，永久高水位会漏掉迟提交的低 ID。
 * 游标只在进程内保存，重启重新回填；搜索故障或部分写入失败时保留原批次重试。
 */
@Component
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class AuditSearchSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditSearchSynchronizer.class);

    private final AuditSearchRepository repository;
    private final AuditIndexSourceMapper sourceMapper;
    private final AuditSearchProperties properties;

    /** 正在扫描的来源；每个来源结束后重新从零扫描下一个来源。 */
    private Source currentSource = Source.OPERATION;

    /** 仅代表当前来源中成功写入并刷新可见的最后主键，不作为永久同步水位。 */
    private long lastIndexedId;

    /** 初始化只在正常同步期间复用，失败后下一次重试会重新确认索引。 */
    private boolean indexInitialized;

    /** 故障发生在一轮中途时，续扫尾部之后还需要从零再扫完整一轮，才能恢复搜索。 */
    private boolean fullPassRequiredAfterRecovery;

    /** 第一次完整回填或故障后的一轮完整同步成功，才允许关键词查询。
     * -- GETTER --
     * 返回索引是否完成首次回填，最近一次同步失败会暂停关键词搜索以避免返回不完整结果。
     */
    @Getter
    private volatile boolean ready;

    /** 保存源查询、搜索写入及批量配置依赖，不在构造阶段连接 MySQL 或 ES。 */
    public AuditSearchSynchronizer(AuditSearchRepository repository,
                                  AuditIndexSourceMapper sourceMapper,
                                  AuditSearchProperties properties) {
        Assert.notNull(repository, "审计搜索仓库不能为空");
        Assert.notNull(sourceMapper, "审计日志源查询不能为空");
        Assert.notNull(properties, "审计搜索配置不能为空");
        this.repository = repository;
        this.sourceMapper = sourceMapper;
        this.properties = properties;
    }

    /**
     * 每次最多读取配置数量的批次，成功写入后才推进游标；轮扫结束后留待下一次调度重新对账。
     * 线程互斥避免同一进程中的手动调用与定时任务同时修改游标，读取和网络写入不包在数据库事务中。
     */
    @Scheduled(fixedDelayString = "${work-order.elasticsearch.audit.sync-delay-ms:30000}",
            initialDelayString = "0")
    public synchronized void synchronize() {
        try {
            // 拒绝无法推进扫描的批量设置，校验失败同样进入统一故障处理。
            validateBatchSettings();
            // 首次运行或故障重试时确认索引可用，正常轮扫复用已经成功的初始化结果。
            initializeIndexIfNecessary();
            // 按本次批数上限继续当前来源，扫完三种来源后结束本次运行，下一次从零重新对账。
            synchronizeAvailableBatches();
        } catch (Exception exception) {
            // 故障处理
            handleSynchronizationFailure(exception);
        }
    }

    /** 拒绝无法推进扫描的批量设置，校验失败同样进入统一故障处理。 */
    private void validateBatchSettings() {
        Assert.isTrue(properties.getBatchSize() > 0, "审计同步批大小必须大于零");
        Assert.isTrue(properties.getBatchesPerRun() > 0, "审计同步每次批数必须大于零");
    }

    /** 首次运行或故障重试时确认索引可用，正常轮扫复用已经成功的初始化结果。 */
    private void initializeIndexIfNecessary() throws IOException {
        if (indexInitialized) {
            return;
        }
        repository.initializeIndex();
        indexInitialized = true;
    }

    /** 按本次批数上限继续当前来源，扫完三种来源后结束本次运行，下一次从零重新对账。 */
    private void synchronizeAvailableBatches() throws IOException {
        for (int batch = 0; batch < properties.getBatchesPerRun(); batch++) {
            // 读取当前来源的批次
            List<AuditSearchDocument> documents = readCurrentSourceBatch();
            // 检查源查询遵守批大小及严格主键递增规则，非法批次不能写入或推进同步游标
            long batchLastId = validateSourceBatch(documents);
            // 先写入并确认整批搜索可见，再推进游标；写入失败时保留原游标供下一次重试
            writeBatchAndAdvanceCursor(documents, batchLastId);

            // 当前来源是否已经扫描完毕
            boolean currentSourceExhausted = documents.size() < properties.getBatchSize();
            // 如果当前来源没有扫描完毕，则继续扫描
            if (!currentSourceExhausted) {
                continue;
            }
            // 如果当前来源已经扫描完毕，则切换来源并判断是否完成一轮同步
            if (moveToNextSource()) {
                completePass();
                return;
            }
        }
    }

    /** 根据当前来源读取已经提交的数据，每次查询都使用独立短读操作。 */
    private List<AuditSearchDocument> readCurrentSourceBatch() {
        switch (currentSource) {
            case OPERATION:
                return sourceMapper.selectOperations(lastIndexedId, properties.getBatchSize());
            case STATUS:
                return sourceMapper.selectStatuses(lastIndexedId, properties.getBatchSize());
            case CONFIGURATION:
                return sourceMapper.selectConfigurations(lastIndexedId, properties.getBatchSize());
            default:
                throw new IllegalStateException("审计同步来源不合法");
        }
    }

    /** 检查源查询遵守批大小及严格主键递增规则，非法批次不能写入或推进同步游标。 */
    private long validateSourceBatch(List<AuditSearchDocument> documents) {
        Assert.notNull(documents, "审计同步批次不能为空");
        Assert.isTrue(documents.size() <= properties.getBatchSize(), "审计同步批次超过配置上限");
        long previousId = lastIndexedId;
        for (AuditSearchDocument document : documents) {
            Assert.notNull(document, "审计同步文档不能为空");
            Assert.isTrue(document.getSourceId() != null && document.getSourceId() > previousId,
                    "审计同步主键必须严格递增");
            Assert.isTrue(currentSource.name().equals(document.getSource()), "审计同步文档来源不匹配");
            previousId = document.getSourceId();
        }
        return previousId;
    }

    /** 先写入并确认整批搜索可见，再推进游标；写入失败时保留原游标供下一次重试。 */
    private void writeBatchAndAdvanceCursor(List<AuditSearchDocument> documents, long batchLastId)
            throws IOException {
        if (documents.isEmpty()) {
            return;
        }
        repository.bulkSave(documents);
        lastIndexedId = batchLastId;
    }

    /** 切换来源并清零当前游标；最后一个来源结束时返回 true 表示一轮完整同步结束。 */
    private boolean moveToNextSource() {
        lastIndexedId = 0;
        switch (currentSource) {
            case OPERATION:
                currentSource = Source.STATUS;
                return false;
            case STATUS:
                currentSource = Source.CONFIGURATION;
                return false;
            case CONFIGURATION:
                currentSource = Source.OPERATION;
                return true;
            default:
                throw new IllegalStateException("审计同步来源不合法");
        }
    }

    /** 正常完整轮扫后允许查询；故障恢复只扫完原轮尾部时，继续等待下一轮完整回填。 */
    private void completePass() {
        if (fullPassRequiredAfterRecovery) {
            ready = false;
            fullPassRequiredAfterRecovery = false;
            return;
        }
        ready = true;
    }

    /** 暂停搜索并安排重新确认索引，保留来源和游标，避免故障时跳过尚未成功写入的批次。 */
    private void handleSynchronizationFailure(Exception exception) {
        ready = false;
        indexInitialized = false;
        // 如果当前轮已经开始了，则故障恢复时需要完整回填
        boolean currentPassAlreadyStarted = currentSource != Source.OPERATION || lastIndexedId > 0;
        fullPassRequiredAfterRecovery = fullPassRequiredAfterRecovery || currentPassAlreadyStarted;

        // ES 异常详情可能回显原始审计数据，只记录当前游标和异常类型。
        LOGGER.error("审计搜索同步失败，将重试当前批次；source={}, afterId={}, errorType={}",
                currentSource, lastIndexedId, exception.getClass().getSimpleName());
    }

    /** 固定的三种日志来源，与源 Mapper 的独立主键扫描一一对应。 */
    private enum Source {
        OPERATION,
        STATUS,
        CONFIGURATION
    }
}
