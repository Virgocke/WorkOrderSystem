package com.WorkOrder.search.service;

import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.repository.TicketSearchRepository;
import com.WorkOrder.security.CurrentUserIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** 处理人工单搜索入口，权限过滤始终来自已认证用户，不信任请求中的用户 ID。 */
@Service
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('HANDLER')")
public class HandlerTicketSearchService {

    private final TicketSearchRepository repository;
    private final CurrentUserIdProvider currentUserIdProvider;

    /** 创建处理人搜索服务，复用仓库及现有 JWT 用户 ID 解析组件。 */
    public HandlerTicketSearchService(TicketSearchRepository repository, CurrentUserIdProvider currentUserIdProvider) {
        this.repository = repository;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    /**
     * 按当前认证用户搜索，只返回候选 ID，由工单服务回表复核权限并读取业务字段。
     * ES 转派投影可能滞后，内部接口也不能直接暴露旧标题、描述等业务内容。
     */
    public TicketSearchPage search(TicketSearchQuery source) throws IOException {
        Assert.notNull(source, "搜索条件不能为空");
        // 获取当前认证用户 ID
        Long handlerId = currentUserIdProvider.get(SecurityContextHolder.getContext().getAuthentication());
        if (handlerId == null || handlerId <= 0) {
            throw new InsufficientAuthenticationException("访问令牌中的用户 ID 必须为正数");
        }

        TicketSearchPage result = repository.search(normalizeQuery(source, handlerId));
        List<TicketSearchDocument> ids = result.getRecords().stream()
                .map(this::toIdOnlyDocument)
                .collect(Collectors.toList());
        return new TicketSearchPage(ids, result.getTotal(), result.getPage(), result.getPageSize());
    }

    /** 创建只携带候选 ID 的独立投影，不修改仓库返回的原始搜索文档。 */
    private TicketSearchDocument toIdOnlyDocument(TicketSearchDocument source) {
        TicketSearchDocument target = new TicketSearchDocument();
        target.setTicketId(source.getTicketId());
        return target;
    }

    /** 仅复制处理人列表支持的条件，屏蔽其他筛选字段，保留原始请求对象。 */
    private TicketSearchQuery normalizeQuery(TicketSearchQuery source, Long handlerId) {
        TicketSearchQuery target = new TicketSearchQuery();
        target.setKeyword(trimToNull(source.getKeyword()));
        String status = trimToNull(source.getStatus());
        target.setStatus("all".equalsIgnoreCase(status) ? null : status);
        String sort = trimToNull(source.getSort());
        target.setSort(sort == null ? "deadline" : sort);
        target.setPage(source.getPage());
        target.setPageSize(source.getPageSize());
        target.setHandlerId(handlerId.toString());
        return target;
    }

    /** 去除首尾空白，将空字符串统一转为不限制条件。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
