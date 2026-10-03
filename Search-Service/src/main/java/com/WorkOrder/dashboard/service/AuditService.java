package com.WorkOrder.dashboard.service;

import com.WorkOrder.dashboard.dto.AuditQuery;
import com.WorkOrder.dashboard.dto.ConfigurationAuditItem;
import com.WorkOrder.dashboard.dto.TicketAuditItem;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.search.service.AuditSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** 审计查询入口：关键词由 ES 检索，无关键词仍由 MySQL 读取分页快照。 */
@Service
@RequiredArgsConstructor
public class AuditService {
    /** 无关键词时读取列表与总数的一致数据库快照。 */
    private final AuditMysqlPageService mysqlReader;

    /** ES 组件默认可关闭，只在有关键词时获取搜索服务。 */
    private final ObjectProvider<AuditSearchService> searchProvider;

    /** 复制校验条件后选择工单审计路径，ES 不可用时明确失败，不降级为 LIKE。 */
    public PageResult<TicketAuditItem> tickets(AuditQuery source) {
        AuditQuery query = normalizeQuery(source);
        if (query.getKeyword() == null) {
            return mysqlReader.tickets(query);
        }

        try {
            return requireSearchService().tickets(query);
        } catch (IOException exception) {
            throw new IllegalStateException("工单审计 ES 查询失败", exception);
        }
    }

    /** 复制校验条件后选择配置审计路径，保留现有响应及准确的 ES 匹配总数。 */
    public PageResult<ConfigurationAuditItem> configurations(AuditQuery source) {
        AuditQuery query = normalizeQuery(source);
        if (query.getKeyword() == null) {
            return mysqlReader.configurations(query);
        }

        try {
            return requireSearchService().configurations(query);
        } catch (IOException exception) {
            throw new IllegalStateException("配置审计 ES 查询失败", exception);
        }
    }

    /** 拒绝空请求并复制客户端条件，避免调用方对象被转义或日期解析修改。 */
    private AuditQuery normalizeQuery(AuditQuery source) {
        if (source == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        return source.normalizedCopy();
    }

    /** 仅在关键词路径获取可选 ES 组件，关闭功能时无关键词查询仍可正常使用。 */
    private AuditSearchService requireSearchService() {
        AuditSearchService searchService = searchProvider.getIfAvailable();
        if (searchService == null) {
            throw new IllegalStateException("审计关键词查询需要启用 work-order.elasticsearch.enabled");
        }
        return searchService;
    }
}
