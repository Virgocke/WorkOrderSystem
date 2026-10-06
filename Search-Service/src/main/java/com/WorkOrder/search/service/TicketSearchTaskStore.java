package com.WorkOrder.search.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.search.mapper.TicketSearchTaskMapper;
import com.WorkOrder.search.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** 只执行短主库事务；所有状态变更先锁控制记录，绝不在此访问 ES/MQ。 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
public class TicketSearchTaskStore {
    private final TicketSearchTaskMapper mapper;

    /** 注入主库 Mapper。 */
    public TicketSearchTaskStore(TicketSearchTaskMapper mapper) { this.mapper = mapper; }

    /** 新事务读取唯一控制记录。 */
    public TicketSearchControlState state() { return mapper.selectState(); }

    /** 新事务读取指定任务，不暴露本地缓存。 */
    public TicketSearchImportTask task(Long id) {
        TicketSearchImportTask task = mapper.selectTask(id);
        if (task == null) { throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND); }
        return task;
    }

    /** 新事务读取周期对账进度。 */
    public TicketSearchReconciliationState reconciliation() { return mapper.selectReconciliation(); }

    /** 请求幂等与单活动代次在同一个控制锁内完成。 */
    public TicketSearchImportTask create(String requestId, String reason, Long adminId) {
        // 同一控制行串行化创建请求，重复点击返回原任务，避免同时建立两套索引。
        TicketSearchControlState state = lock();
        TicketSearchImportTask existing = mapper.selectByRequest(requestId);
        if (existing != null) { return existing; }
        if (!"NOT_READY".equals(state.getPhase()) && !"READY".equals(state.getPhase())) { conflict(); }
        TicketSearchImportTask task = new TicketSearchImportTask();
        task.setRequestId(requestId); task.setReason(reason); task.setCreatedBy(adminId);
        mapper.insertTask(task);
        task = mapper.selectTask(task.getId());
        // 任务 ID 同时标识本次索引代次，重启后仍能找到同一个目标。
        task.setGeneration(task.getId());
        task.setTargetIndex("wo-ticket-v3-" + task.getId());
        task.setWriteAlias("wo-ticket-write-" + task.getId());
        mapper.updateTask(task);
        state.setCurrentJobId(task.getId()); state.setPhase("IMPORTING");
        // 尚未实现目标校验时不沿用旧写目标；消息异常会由 MQ 重试。
        state.setWriteGeneration(null); state.setWriteAlias(null); state.setLastError(null);
        mapper.updateState(state);
        TicketSearchReconciliationState reconcile = mapper.selectReconciliation();
        if (reconcile != null) {
            // 重建后旧代次的核对进度已失效，新索引发布后需要从头对账。
            reconcile.setGeneration(null); reconcile.setScanUpperId(null); reconcile.setLastTicketId(0L);
            reconcile.setLastFullSuccessAt(null); reconcile.setLastError(null);
            mapper.updateReconciliation(reconcile);
        }
        return mapper.selectTask(task.getId());
    }

    /** 只领取当前未验证任务；数据库时间判断是否可接管扫描。 */
    public TicketSearchImportTask claim(String owner, int leaseSeconds) {
        TicketSearchControlState state = lock();
        // 发布可能已经在 ES 生效，不能由扫描租约过期触发自动接管。
        if (state.getCurrentJobId() == null || !("IMPORTING".equals(state.getPhase())
                || "VERIFYING".equals(state.getPhase()))) { return null; }
        String token = token();
        if (mapper.acquireLease(state.getCurrentJobId(), owner, token, leaseSeconds) != 1) { return null; }
        return mapper.selectTask(state.getCurrentJobId());
    }

    /** 保持当前任务、状态和未过期租约均匹配才续租。 */
    public boolean renew(Long id, String token, int seconds) {
        TicketSearchControlState state = lock();
        return Objects.equals(state.getCurrentJobId(), id)
                && ("IMPORTING".equals(state.getPhase()) || "VERIFYING".equals(state.getPhase()))
                && mapper.renewLease(id, token, seconds) == 1;
    }

    /** 受管目标准备完成后先激活写目标，之后才取得本轮 MAX(id)。 */
    public void activate(Long id, String token) {
        TicketSearchControlState state = lock();
        TicketSearchImportTask task = owned(state, id, token, "IMPORTING", null);
        if (task.getTargetInitializedAt() == null) {
            // 先让消息消费者写入新索引，再由导入程序取扫描上界，覆盖扫描期间的新变更。
            task.setTargetInitializedAt(LocalDateTime.now()); mapper.updateTask(task);
            state.setWriteGeneration(task.getGeneration()); state.setWriteAlias(task.getWriteAlias());
            mapper.updateState(state);
        }
    }

    /** 保存有限上界；单独标记初始化，空表 upper=0 也有明确完成事实。 */
    public void initializeScan(Long id, String token, long upper) {
        TicketSearchImportTask task = owned(lock(), id, token, "IMPORTING", null);
        if (task.getScanStartedAt() == null) {
            // 空表的上界也是 0，用独立时间标记区分“已初始化”和“尚未初始化”。
            task.setScanUpperId(upper); task.setScanStartedAt(LocalDateTime.now()); mapper.updateTask(task);
        }
    }

    /** 整批完成后在控制锁内同时校验旧游标、令牌、阶段和代次再提交。 */
    public void advance(Long id, String token, String phase, long expectedCursor, long cursor, int count) {
        TicketSearchImportTask task = owned(lock(), id, token, phase, expectedCursor);
        task.setLastTicketId(cursor); task.setScannedCount(task.getScannedCount() + count);
        task.setCoveredCount(task.getCoveredCount() + count); task.setLastError(null);
        mapper.updateTask(task);
    }

    /** 导入结束后建立新的从零开始验证轮次。 */
    public void beginVerification(Long id, String token, long expectedCursor, long upper) {
        TicketSearchControlState state = lock();
        TicketSearchImportTask task = owned(state, id, token, "IMPORTING", expectedCursor);
        // 导入时源数据仍可能变化，重新取上界并从头检查，不能沿用导入末尾的游标。
        task.setStatus("VERIFYING"); task.setScanUpperId(upper); task.setLastTicketId(0L);
        task.setScannedCount(0L); task.setCoveredCount(0L); task.setScanStartedAt(LocalDateTime.now());
        task.setVerifiedAt(null); task.setRefreshCompletedAt(null); mapper.updateTask(task);
        state.setPhase("VERIFYING"); mapper.updateState(state);
    }

    /** 仅在完整验证扫描及目标 refresh 都成功之后保存完成事实。 */
    public void verified(Long id, String token, long expectedCursor) {
        TicketSearchImportTask task = owned(lock(), id, token, "VERIFYING", expectedCursor);
        LocalDateTime now = LocalDateTime.now();
        // 只登记验证完成；保持 VERIFYING，等待管理员核对消息健康后独立发布。
        task.setVerifiedAt(now); task.setRefreshCompletedAt(now); task.setLastError(null);
        mapper.updateTask(task); mapper.releaseLease(id, token);
    }

    /** 保存批次失败并保持原游标；失去租约者不能改变新工作者的任务。 */
    public void fail(Long id, String token, String safeError, Long failedTicketId) {
        TicketSearchControlState state = lock();
        // 旧工作者的迟到异常不能把已经接管的任务标成失败。
        if (!Objects.equals(state.getCurrentJobId(), id) || mapper.ownsLease(id, token) != 1) { return; }
        TicketSearchImportTask task = mapper.selectTask(id);
        if (!Objects.equals(state.getPhase(), task.getStatus())
                || !("IMPORTING".equals(task.getStatus()) || "VERIFYING".equals(task.getStatus()))) { return; }
        // 保留出错前阶段和已完成游标，恢复时重做尚未确认成功的批次。
        task.setResumePhase(task.getStatus()); task.setStatus("FAILED"); task.setLastError(safeError);
        task.setFailureCount(task.getFailureCount() + 1); task.setLastFailedTicketId(failedTicketId);
        mapper.updateTask(task); mapper.releaseLease(id, token);
        state.setPhase("FAILED"); state.setLastError(safeError); mapper.updateState(state);
    }

    /** 正常释放自身租约，不释放其他工作者的新令牌。 */
    public void release(Long id, String token) { mapper.releaseLease(id, token); }

    /** 修复后恢复当前 FAILED 任务，绝不分配另一代次。 */
    public TicketSearchImportTask resume(Long id) {
        TicketSearchControlState state = lock();
        TicketSearchImportTask task = current(state, id);
        if (!"FAILED".equals(state.getPhase()) || !"FAILED".equals(task.getStatus())
                || !("IMPORTING".equals(task.getResumePhase()) || "VERIFYING".equals(task.getResumePhase()))) { conflict(); }
        // 沿用原目标和游标，但清除完成标记，避免恢复后直接使用旧验证结论发布。
        task.setStatus(task.getResumePhase()); task.setLastError(null); task.setLastFailedTicketId(null);
        task.setVerifiedAt(null); task.setRefreshCompletedAt(null); mapper.updateTask(task);
        state.setPhase(task.getStatus()); state.setLastError(null); mapper.updateState(state);
        return mapper.selectTask(id);
    }

    /** 完成验证后登记唯一发布操作；网络请求在此事务结束后执行。 */
    public TicketSearchImportTask beginPublish(Long id, String owner, Long adminId, String manifest) {
        TicketSearchControlState state = lock();
        TicketSearchImportTask task = current(state, id);
        // 阶段名称不能代替完成事实：目标、验证、刷新和部署确认必须同时有效。
        if (!"VERIFYING".equals(state.getPhase()) || !"VERIFYING".equals(task.getStatus())
                || task.getTargetInitializedAt() == null || task.getScanStartedAt() == null
                || task.getVerifiedAt() == null || task.getRefreshCompletedAt() == null
                || task.getVerifiedAt().isBefore(task.getTargetInitializedAt())
                || task.getRefreshCompletedAt().isBefore(task.getVerifiedAt())
                || adminId == null || adminId <= 0 || !StringUtils.hasText(manifest) || manifest.length() > 2000
                || task.getLastError() != null || state.getLastError() != null
                || !Objects.equals(state.getWriteGeneration(), task.getGeneration())
                || !Objects.equals(state.getWriteAlias(), task.getWriteAlias())) { conflict(); }
        // 先持久登记唯一发布身份，再由外层服务调用 ES；崩溃后仍能核对原操作。
        task.setStatus("PUBLISHING"); task.setPublisherOwner(owner); task.setPublisherToken(token());
        task.setPublishStartedAt(LocalDateTime.now()); task.setDeploymentConfirmedBy(adminId);
        task.setDeploymentConfirmedAt(LocalDateTime.now()); task.setDeploymentManifest(manifest);
        mapper.updateTask(task); state.setPhase("PUBLISHING"); mapper.updateState(state);
        return mapper.selectTask(id);
    }

    /** 实际别名确认后只允许同一个发布令牌补记 READY。 */
    public TicketSearchImportTask published(Long id, String token) {
        // 同一发布令牌才能确认完成，避免迟到响应把另一项操作误记为 READY。
        TicketSearchControlState state = lock(); TicketSearchImportTask task = publishing(state, id, token);
        task.setStatus("READY"); task.setLastError(null); mapper.updateTask(task);
        state.setPhase("READY"); state.setPublishedGeneration(task.getGeneration()); state.setLastError(null);
        mapper.updateState(state); return mapper.selectTask(id);
    }

    /** 发布结果不确定时保留 PUBLISHING 与令牌，禁止定时自动接管。 */
    public void publishUncertain(Long id, String token) {
        TicketSearchControlState state = lock(); TicketSearchImportTask task = publishing(state, id, token);
        // 网络超时不等于别名未切换，保留原身份和暂停状态，等待回读或人工恢复。
        task.setLastError("PUBLISH_RESULT_UNCERTAIN"); mapper.updateTask(task);
        state.setLastError(task.getLastError()); mapper.updateState(state);
    }

    /** 在读取实际别名前保存管理员对原发布进程停止的确认依据。 */
    public void confirmPublicationRecovery(Long id, String token, Long adminId, String reason) {
        TicketSearchImportTask task = publishing(lock(), id, token);
        if (adminId == null || adminId <= 0 || !StringUtils.hasText(reason) || reason.length() > 500) { conflict(); }
        task.setPublicationRecoveryBy(adminId);
        task.setPublicationRecoveryAt(LocalDateTime.now());
        task.setPublicationRecoveryReason(reason);
        mapper.updateTask(task);
    }

    /** 明确确认原发布者停止且别名未切换后，允许原任务重新发布。 */
    public TicketSearchImportTask retryPublication(Long id, String token) {
        TicketSearchControlState state = lock(); TicketSearchImportTask task = publishing(state, id, token);
        task.setStatus("VERIFYING"); task.setLastError(null); task.setPublisherToken(null); task.setPublisherOwner(null);
        mapper.updateTask(task); state.setPhase("VERIFYING"); state.setLastError(null); mapper.updateState(state);
        return mapper.selectTask(id);
    }

    /** READY 时领取独立对账租约；新代次必从零重扫。 */
    public TicketSearchReconciliationState claimReconciliation(String token, int seconds) {
        TicketSearchControlState state = lock();
        if (!"READY".equals(state.getPhase()) || !Objects.equals(state.getWriteGeneration(), state.getPublishedGeneration())) { return null; }
        if (mapper.acquireReconciliation(token, seconds) != 1) { return null; }
        TicketSearchReconciliationState result = mapper.selectReconciliation();
        if (!Objects.equals(result.getGeneration(), state.getWriteGeneration())) {
            // 对账游标只属于一个索引代次，不能拿旧索引的进度跳过新索引的数据。
            result.setGeneration(state.getWriteGeneration()); result.setScanUpperId(null); result.setLastTicketId(0L);
            result.setLastFullSuccessAt(null); result.setLastError(null); mapper.updateReconciliation(result);
        }
        return result;
    }

    /** 对账续租与任务代次同时校验。 */
    public boolean renewReconciliation(Long generation, String token, int seconds) {
        TicketSearchControlState state = lock();
        return "READY".equals(state.getPhase()) && Objects.equals(state.getWriteGeneration(), generation)
                && mapper.renewReconciliation(token, seconds) == 1;
    }

    /** 保存对账本轮上界，仅初始化一次。 */
    public void initializeReconciliation(Long generation, String token, long upper) {
        TicketSearchReconciliationState state = ownedReconciliation(lock(), generation, token, null);
        if (state.getScanUpperId() == null) { state.setScanUpperId(upper); mapper.updateReconciliation(state); }
    }

    /** 整批对账成功才推进对账游标。 */
    public void advanceReconciliation(Long generation, String token, long expected, long cursor) {
        TicketSearchReconciliationState state = ownedReconciliation(lock(), generation, token, expected);
        state.setLastTicketId(cursor); state.setLastError(null); mapper.updateReconciliation(state);
    }

    /** 每轮完成后复位游标与上界，下一轮覆盖迟提交的小 ID。 */
    public void completeReconciliation(Long generation, String token, long expected) {
        TicketSearchReconciliationState state = ownedReconciliation(lock(), generation, token, expected);
        state.setLastFullSuccessAt(LocalDateTime.now()); state.setCompletedRounds(state.getCompletedRounds() + 1);
        // 自增 ID 的分配顺序不等于事务提交顺序，每轮从头扫描才能补到迟提交的小 ID。
        state.setLastTicketId(0L); state.setScanUpperId(null); state.setLastError(null); mapper.updateReconciliation(state);
    }

    /** 对账失败保留游标，后续重试；不改历史导入完成事实。 */
    public void failReconciliation(Long generation, String token, String error) {
        TicketSearchControlState control = lock();
        if (!"READY".equals(control.getPhase()) || !Objects.equals(control.getWriteGeneration(), generation)
                || mapper.ownsReconciliation(token) != 1) { return; }
        TicketSearchReconciliationState state = mapper.selectReconciliation();
        state.setLastError(error); mapper.updateReconciliation(state);
    }

    /** 仅释放原对账令牌。 */
    public void releaseReconciliation(String token) { mapper.releaseReconciliation(token); }

    private TicketSearchControlState lock() {
        TicketSearchControlState state = mapper.lockState();
        if (state == null) { throw new IllegalStateException("工单搜索迁移尚未执行"); }
        return state;
    }

    private TicketSearchImportTask current(TicketSearchControlState state, Long id) {
        TicketSearchImportTask task = mapper.selectTask(id);
        if (task == null) { throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND); }
        if (!Objects.equals(state.getCurrentJobId(), id)) { conflict(); }
        return task;
    }

    private TicketSearchImportTask owned(TicketSearchControlState state, Long id, String token, String phase, Long cursor) {
        TicketSearchImportTask task = current(state, id);
        // 令牌防旧工作者提交，旧游标防重复推进；两项都通过后才允许修改进度。
        if (!phase.equals(state.getPhase()) || !phase.equals(task.getStatus())
                || mapper.ownsLease(id, token) != 1 || (cursor != null && !cursor.equals(task.getLastTicketId()))) {
            throw new LostLeaseException();
        }
        // 已激活目标还要匹配共享写代次，避免重建后继续提交旧索引的扫描进度。
        if (task.getTargetInitializedAt() != null && (!Objects.equals(state.getWriteGeneration(), task.getGeneration())
                || !Objects.equals(state.getWriteAlias(), task.getWriteAlias()))) { throw new LostLeaseException(); }
        return task;
    }

    private TicketSearchImportTask publishing(TicketSearchControlState state, Long id, String token) {
        TicketSearchImportTask task = current(state, id);
        if (!"PUBLISHING".equals(state.getPhase()) || !"PUBLISHING".equals(task.getStatus())
                || token == null || !token.equals(task.getPublisherToken())) { conflict(); }
        return task;
    }

    private TicketSearchReconciliationState ownedReconciliation(TicketSearchControlState control, Long generation, String token, Long cursor) {
        TicketSearchReconciliationState state = mapper.selectReconciliation();
        if (!"READY".equals(control.getPhase()) || !Objects.equals(control.getWriteGeneration(), generation)
                || state == null || !Objects.equals(state.getGeneration(), generation)
                || mapper.ownsReconciliation(token) != 1 || (cursor != null && !cursor.equals(state.getLastTicketId()))) {
            throw new LostLeaseException();
        }
        return state;
    }

    /** 生成不含分隔符的随机领取或发布令牌。 */
    public static String token() { return UUID.randomUUID().toString().replace("-", ""); }

    private static void conflict() { throw new SystemException(SystemExceptionEnum.TICKET_SEARCH_JOB_CONFLICT); }

    /** 旧扫描工作者正常退出的信号；不会把新持有者标记失败。 */
    public static class LostLeaseException extends RuntimeException {
        public LostLeaseException() { super("工单搜索扫描租约或代次已改变"); }
    }
}
