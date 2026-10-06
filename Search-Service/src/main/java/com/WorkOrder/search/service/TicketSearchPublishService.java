package com.WorkOrder.search.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.search.config.ElasticsearchProperties;
import com.WorkOrder.search.model.*;
import com.WorkOrder.search.model.admin.TicketSearchResumeRequest;
import com.WorkOrder.search.repository.TicketSearchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 独立发布操作；数据库令牌不冒充 ES 栅栏，结果不确定时保持同任务暂停。 */
@Service
@ConditionalOnProperty(prefix="work-order.elasticsearch.ticket-sync", name="enabled", havingValue="true")
public class TicketSearchPublishService {
    private final String owner="publisher-" + TicketSearchTaskStore.token();
    private final Set<Long> active=ConcurrentHashMap.newKeySet();
    private final TicketSearchTaskStore store;
    private final TicketSearchIndexService indexes;
    private final TicketSearchRepository repository;
    private final TicketSearchRuntimeStatusService runtime;
    private final ElasticsearchProperties properties;

    /** 注入既有索引和运行观测服务；不在构造时访问网络。 */
    public TicketSearchPublishService(TicketSearchTaskStore store, TicketSearchIndexService indexes,
                                      TicketSearchRepository repository, TicketSearchRuntimeStatusService runtime,
                                      ElasticsearchProperties properties) {
        this.store=store; this.indexes=indexes; this.repository=repository; this.runtime=runtime; this.properties=properties;
    }

    /** 主库登记发布身份，事务外 refresh 和原子换别名，确认后补记 READY。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchImportTask publish(Long id, Long adminId, String manifest) throws IOException {
        if (!active.add(id)) { conflict(); }
        TicketSearchImportTask reserved=null;
        try {
            TicketSearchImportTask task=store.task(id);
            TicketSearchControlState control=store.state();
            // 重复发布请求先核对实际读别名，数据库里的 READY 不能代替 ES 的确认。
            if ("READY".equals(task.getStatus()) && Objects.equals(control.getCurrentJobId(),id)) {
                TicketSearchWriteTarget published=TicketSearchReadinessService.validatedPublishedTarget(control,task);
                indexes.validatePublishedReadAlias(published);
                return task;
            }
            runtime.requirePublishable();
            indexes.validateWriteTarget(task.target());
            Set<String> previous=expectedPrevious();
            // 短事务先登记唯一发布令牌，再在事务外换别名，避免持有数据库锁等待网络。
            reserved=store.beginPublish(id,owner,adminId,manifest);
            repository.refresh(reserved.target());
            indexes.publishReadAlias(reserved.target(),previous);
            indexes.validatePublishedReadAlias(reserved.target());
            return store.published(id,reserved.getPublisherToken());
        } catch (Exception exception) {
            if (reserved != null) {
                // ES 可能已执行；绝不把超时直接当作失败并开另一代任务。
                try {
                    indexes.validatePublishedReadAlias(reserved.target());
                    return store.published(id,reserved.getPublisherToken());
                } catch (Exception uncertain) {
                    // 回读也失败时保持 PUBLISHING，阻止自动接管或新任务覆盖这次未确认的发布。
                    store.publishUncertain(id,reserved.getPublisherToken());
                }
            }
            if (exception instanceof IOException) { throw (IOException)exception; }
            if (exception instanceof RuntimeException) { throw (RuntimeException)exception; }
            throw new IOException("PUBLISH_RESULT_UNCERTAIN",exception);
        } finally { active.remove(id); }
    }

    /** 先确认原发布进程停止，再按实际别名恢复同一任务；没有自动超时接管。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchImportTask recover(Long id, TicketSearchResumeRequest request, Long adminId) throws IOException {
        if (!active.add(id)) { conflict(); }
        try {
            TicketSearchImportTask task=store.task(id);
            if (request == null || !request.isOriginalPublisherStopped() || !StringUtils.hasText(request.getReason())
                    || !StringUtils.hasText(request.getPublisherToken())
                    || !request.getPublisherToken().equals(task.getPublisherToken())
                    || !"PUBLISHING".equals(task.getStatus())) { conflict(); }
            // 数据库令牌挡不住旧进程迟到的 ES 请求，恢复前必须确认原发布者已经停止。
            store.confirmPublicationRecovery(id,request.getPublisherToken(),adminId,request.getReason().trim());
            indexes.validateWriteTarget(task.target());
            Set<String> actual=indexes.inspectReadAlias();
            // 读别名已切到本任务时只补记 READY，不再次发送换别名请求。
            if (actual.equals(Collections.singleton(task.getTargetIndex()))) {
                indexes.validatePublishedReadAlias(task.target());
                return store.published(id,request.getPublisherToken());
            }
            Set<String> previous=expectedPrevious();
            if (!actual.equals(previous) && !(previous.size()==1
                    && previous.contains(properties.getTicketIndexName()) && actual.isEmpty())) { conflict(); }
            // 确认读入口仍未切到本代次后，只恢复原任务；下一次发布会重新执行检查。
            return store.retryPublication(id,request.getPublisherToken());
        } finally { active.remove(id); }
    }

    /** 首次仅接管明确配置的旧入口；后续仅接管数据库登记的已发布代次。 */
    private Set<String> expectedPrevious() throws IOException {
        TicketSearchControlState state=store.state();
        if (state.getPublishedGeneration() == null) {
            Set<String> actual=indexes.inspectReadAlias();
            if (actual.isEmpty()) { return Collections.emptySet(); }
            if (!actual.equals(Collections.singleton(properties.getTicketIndexName()))) { conflict(); }
            return actual;
        }
        return Collections.singleton(store.task(state.getPublishedGeneration()).getTargetIndex());
    }

    private static void conflict() { throw new SystemException(SystemExceptionEnum.TICKET_SEARCH_JOB_CONFLICT); }
}
