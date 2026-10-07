package com.WorkOrder.dashboard.service;

import com.WorkOrder.dashboard.dto.AuditQuery;
import com.WorkOrder.dashboard.dto.ConfigurationAuditItem;
import com.WorkOrder.dashboard.dto.TicketAuditItem;
import com.WorkOrder.dashboard.mapper.AuditMapper;
import com.WorkOrder.model.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 无关键词分页保留 MySQL 一致快照，ES 网络请求不进入数据库事务。
 */
@Service
@RequiredArgsConstructor
public class AuditMysqlPageService {
    private final AuditMapper mapper;

    /**
     * 在同一只读快照读取工单审计列表和总数，条件已由入口规范化。
     *
     * @param query 审计查询条件
     * @return 工单审计条目的分页结果
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<TicketAuditItem> tickets(AuditQuery query) {
        return new PageResult<>(mapper.selectTickets(query), mapper.countTickets(query), query.getPage(), query.getPageSize());
    }

    /**
     * 在同一只读快照读取配置审计列表和总数，条件已由入口规范化。
     *
     * @param query 审计查询条件
     * @return 配置审计条目的分页结果
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<ConfigurationAuditItem> configurations(AuditQuery query) {
        return new PageResult<>(mapper.selectConfigurations(query), mapper.countConfigurations(query), query.getPage(), query.getPageSize());
    }
}
