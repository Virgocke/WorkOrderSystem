package com.WorkOrder.search.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.search.config.TicketSearchSyncProperties;
import com.WorkOrder.search.mapper.TicketIndexSourceMapper;
import com.WorkOrder.search.model.*;
import com.WorkOrder.search.model.admin.*;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.validation.Validator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** 管理入口负责身份、参数和开关，工作者只操作已登记任务。 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class TicketSearchImportService {
    private final TicketSearchTaskStore store;
    private final TicketSearchSyncProperties properties;
    private final TicketSearchReadinessService readiness;
    private final TicketSearchRuntimeStatusService runtime;
    private final ObjectProvider<TicketSearchPublishService> publishers;
    private final ObjectProvider<TicketProjectionService> projections;
    private final TicketIndexSourceMapper sources;
    private final CurrentUserIdProvider users;
    private final Validator validator;
    private final ObjectMapper json;

    /** 网络组件采用可选依赖，使关闭 ES 后管理入口仍可说明未就绪状态。 */
    public TicketSearchImportService(TicketSearchTaskStore store, TicketSearchSyncProperties properties,
                                     TicketSearchReadinessService readiness, TicketSearchRuntimeStatusService runtime,
                                     ObjectProvider<TicketSearchPublishService> publishers,
                                     ObjectProvider<TicketProjectionService> projections, TicketIndexSourceMapper sources,
                                     CurrentUserIdProvider users, Validator validator, ObjectMapper json) {
        this.store=store; this.properties=properties; this.readiness=readiness; this.runtime=runtime;
        this.publishers=publishers; this.projections=projections; this.sources=sources;
        this.users=users; this.validator=validator; this.json=json;
    }

    /** 不依赖本机历史 ready 标记，每次重新核对共享状态及外部目标。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchAdminStatus status() {
        TicketSearchAdminStatus status=new TicketSearchAdminStatus(); status.setSyncEnabled(properties.isEnabled());
        try {
            TicketSearchControlState control=store.state(); status.setControl(control);
            if (control != null && control.getCurrentJobId() != null) { status.setTask(store.task(control.getCurrentJobId())); }
            status.setReconciliation(store.reconciliation());
            readiness.requireReady(); status.setReady(true);
        } catch (Exception exception) { status.setError(exception.getClass().getSimpleName()); }
        // 就绪检查失败也继续采集积压，便于区分索引未发布和消息链路异常。
        status.setRuntime(runtime.inspect()); return status;
    }

    /** 同一 requestId 永远返回原任务；新代次通过共享控制锁登记。 */
    public TicketSearchImportTask create(TicketSearchCreateImportRequest request) {
        enabled(); validate(request);
        // 这里只登记任务，历史扫描由定时工作者执行，接口不等待全量工单写入 ES。
        return store.create(request.getRequestId(),request.getReason().trim(),adminId());
    }

    /** 查询指定任务，只返回数据库保存的事实。 */
    public TicketSearchImportTask task(Long id) { positive(id); return store.task(id); }

    /** 失败扫描续跑原代次；PUBLISHING 则走人工确认后回读别名的恢复路径。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchImportTask resume(Long id, TicketSearchResumeRequest request) throws IOException {
        enabled(); positive(id); if (request != null) { validate(request); }
        TicketSearchImportTask task=store.task(id);
        // 发布超时可能已经换过读别名，必须先回读 ES；普通扫描失败则从原游标续跑。
        if ("PUBLISHING".equals(task.getStatus())) { return publisher().recover(id,request,adminId()); }
        return store.resume(id);
    }

    /** 发布请求逐项记录源实例部署核对，重复实例或超过存储上限的清单拒绝。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchImportTask publish(Long id, TicketSearchPublishRequest request) throws IOException {
        enabled(); positive(id); validate(request);
        // 部署确认需要覆盖每个源实例；同一实例重复填报不能算作多份证明。
        Set<String> ids=new HashSet<>();
        for (TicketSearchPublishRequest.SourceInstance source : request.getSourceInstances()) {
            if (!ids.add(source.getInstanceId().trim())) { illegal(); }
        }
        String manifest=json.writeValueAsString(request);
        if (manifest.length() > 2000) { illegal(); }
        return publisher().publish(id,adminId(),manifest);
    }

    /** 单工单修复只能回源当前完整行和源版本，不接受自定义正文或版本。 */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TicketSearchWriteOutcome repair(Long ticketId) throws IOException {
        enabled(); positive(ticketId);
        TicketIndexSource source=sources.selectById(ticketId);
        if (source == null) { throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND); }
        TicketProjectionService projection=projections.getIfAvailable();
        if (projection == null) { unavailable(); }
        // 最终写入仍由统一投影再次回源，版本和完整内容必须来自同一条记录。
        return projection.synchronize(ticketId,source.getSourceVersion());
    }

    private TicketSearchPublishService publisher() {
        TicketSearchPublishService publisher=publishers.getIfAvailable();
        if (publisher == null) { unavailable(); }
        return publisher;
    }

    private Long adminId() {
        Long id=users.get(SecurityContextHolder.getContext().getAuthentication()); positive(id); return id;
    }

    private void validate(Object request) { if (request == null || !validator.validate(request).isEmpty()) { illegal(); } }
    private void enabled() { if (!properties.isEnabled()) { unavailable(); } }
    private static void positive(Long id) { if (id == null || id <= 0) { illegal(); } }
    private static void illegal() { throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT); }
    private static void unavailable() { throw new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY); }
}
