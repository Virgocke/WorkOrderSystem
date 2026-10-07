package com.WorkOrder.search.service;

import com.WorkOrder.search.mapper.TicketSearchWriteTargetMapper;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import com.WorkOrder.search.model.TicketSearchWriteTargetRecord;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 每次从共享控制记录解析受管目标；只读取，不推进任务、不创建索引。
 */
public class TicketSearchWriteTargetResolver {
    private static final Set<String> WRITABLE_PHASES = new HashSet<>(
            Arrays.asList("IMPORTING", "VERIFYING", "PUBLISHING", "READY", "FAILED"));

    private final TicketSearchWriteTargetMapper mapper;

    /**
     * 目标查询使用主业务数据源，与源工单查询保持同一数据库来源。
     *
     * @param mapper 工单搜索写入目标数据访问器
     */
    public TicketSearchWriteTargetResolver(TicketSearchWriteTargetMapper mapper) {
        Assert.notNull(mapper, "工单搜索写目标查询不能为空");
        this.mapper = mapper;
    }

    /**
     * 在新的短事务中取得目标，避免外层 REPEATABLE READ 旧快照或会话缓存。
     * 返回后事务结束，后续索引校验和写入不占用该事务。
     *
     * @return 工单索引写入目标
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, rollbackFor = Exception.class)
    public TicketSearchWriteTarget resolve() {
        TicketSearchWriteTargetRecord record = mapper.selectCurrent();
        if (record == null || !Long.valueOf(1).equals(record.getControlId())
                || !WRITABLE_PHASES.contains(record.getPhase())) {
            throw new IllegalStateException("工单搜索尚未登记有效写目标");
        }
        // 控制记录决定当前任务，任务记录决定物理目标，两者必须属于同一代次。
        if (!positive(record.getCurrentJobId()) || !positive(record.getWriteGeneration())
                || !record.getCurrentJobId().equals(record.getJobId())
                || !record.getWriteGeneration().equals(record.getJobGeneration())) {
            throw new IllegalStateException("工单搜索控制记录与任务的 ID 或代次不一致");
        }
        String expectedIndex = "wo-ticket-v3-" + record.getJobId();
        String expectedAlias = "wo-ticket-write-" + record.getJobId();
        // 只接受服务端按任务 ID 生成的目标，不能把其他任务或业务读别名当作写入口。
        if (!expectedIndex.equals(record.getTargetIndex())
                || !expectedAlias.equals(record.getWriteAlias())
                || !expectedAlias.equals(record.getJobWriteAlias())) {
            throw new IllegalStateException("工单搜索目标不符合当前任务登记的专属索引和写别名");
        }
        // FAILED 保留同代次增量同步；不会清错误、恢复任务或开放关键词查询。
        return new TicketSearchWriteTarget(record.getJobId(), record.getWriteGeneration(),
                record.getTargetIndex(), record.getWriteAlias());
    }

    /**
     * 判断可空任务字段是否提供正整数。
     *
     * @param value 数据库中待检查的可空任务 ID 或重建代次
     * @return 值非 null 且大于 0 时为 true
     */
    private boolean positive(Long value) {
        return value != null && value > 0;
    }
}
