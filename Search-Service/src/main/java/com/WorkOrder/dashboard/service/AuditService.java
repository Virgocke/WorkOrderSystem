package com.WorkOrder.dashboard.service;

import com.WorkOrder.dashboard.dto.*;
import com.WorkOrder.dashboard.mapper.AuditMapper;
import com.WorkOrder.model.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 分页数据与总数在同一只读快照读取。 */
@Service
@RequiredArgsConstructor
public class AuditService {
    /** 查询或序列化所用组件。 */
    private final AuditMapper mapper;

    /** 校验筛选条件，在一致快照中读取工单审计列表与总数。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<TicketAuditItem> tickets(AuditQuery query) {
        query.validate();
        return new PageResult<>(mapper.selectTickets(query), mapper.countTickets(query), query.getPage(), query.getPageSize());
    }

    /** 校验筛选条件，在一致快照中读取配置审计列表与总数。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<ConfigurationAuditItem> configurations(AuditQuery query) {
        query.validate();
        return new PageResult<>(mapper.selectConfigurations(query), mapper.countConfigurations(query), query.getPage(), query.getPageSize());
    }
}
