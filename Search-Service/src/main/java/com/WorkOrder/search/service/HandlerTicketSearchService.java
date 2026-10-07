package com.WorkOrder.search.service;

import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.repository.TicketSearchRepository;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import org.elasticsearch.ElasticsearchStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.WorkOrder.security.CurrentUserIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 处理人工单搜索入口，权限过滤始终来自已认证用户，不信任请求中的用户 ID。
 */
@Service
@PreAuthorize("hasRole('HANDLER')")
public class HandlerTicketSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandlerTicketSearchService.class);

    private final Optional<TicketSearchRepository> repository;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final TicketSearchReadinessService readinessService;

    /**
     * 创建处理人搜索服务，复用现有 JWT 用户 ID 解析组件及共享就绪门禁。
     *
     * @param repository ES 关闭时允许缺失的工单搜索仓库
     * @param currentUserIdProvider 已验签令牌中的当前用户 ID 解析组件
     * @param readinessService 基于主库及真实别名的查询就绪门禁
     */
    public HandlerTicketSearchService(Optional<TicketSearchRepository> repository,
                                      CurrentUserIdProvider currentUserIdProvider,
                                      TicketSearchReadinessService readinessService) {
        this.repository = repository;
        this.currentUserIdProvider = currentUserIdProvider;
        this.readinessService = readinessService;
    }

    /**
     * 按当前认证用户搜索，只返回候选 ID，由工单服务回表复核权限并读取业务字段。
     * ES 转派投影可能滞后，内部接口也不能直接暴露旧标题、描述等业务内容。
     *
     * @param source 处理人搜索条件，身份筛选由当前认证覆盖
     * @return 只含候选 ID 的搜索页
     * @throws SystemException 同步未就绪、ES 查询失败或查询期间已发布目标发生变化
     * @throws IOException 处理过程中发生IO异常时
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TicketSearchPage search(TicketSearchQuery source) throws IOException {
        Assert.notNull(source, "搜索条件不能为空");
        // 获取当前认证用户 ID
        Long handlerId = currentUserIdProvider.get(SecurityContextHolder.getContext().getAuthentication());
        if (handlerId == null || handlerId <= 0) {
            throw new InsufficientAuthenticationException("访问令牌中的用户 ID 必须为正数");
        }

        // 固定本次查询的发布代次；搜索执行期间发生重建时，结束校验会拒绝返回旧结果。
        TicketSearchWriteTarget target = readinessService.requireReady();
        TicketSearchRepository searchRepository = repository.orElseThrow(
                () -> new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY));
        TicketSearchPage result;
        try {
            result = searchRepository.search(normalizeQuery(source, handlerId));
        } catch (IOException | ElasticsearchStatusException exception) {
            LOGGER.warn("处理人工单关键词查询不可用，generation={}，failureType={}",
                    target.getGeneration(), exception.getClass().getSimpleName());
            SystemException unavailable = new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY);
            unavailable.initCause(exception);
            throw unavailable;
        }
        readinessService.assertStillReady(target);
        // 转派后的 ES 权限投影可能滞后，只返回候选 ID，由工单服务用 MySQL 复核当前权限和详情。
        List<TicketSearchDocument> ids = result.getRecords().stream()
                .map(this::toIdOnlyDocument)
                .collect(Collectors.toList());
        return new TicketSearchPage(ids, result.getTotal(), result.getPage(), result.getPageSize());
    }

    /**
     * 创建只携带候选 ID 的独立投影，不修改仓库返回的原始搜索文档。
     *
     * @param source 待转换或读取的源数据
     * @return 工单搜索文档
     */
    private TicketSearchDocument toIdOnlyDocument(TicketSearchDocument source) {
        TicketSearchDocument target = new TicketSearchDocument();
        target.setTicketId(source.getTicketId());
        return target;
    }

    /**
     * 仅复制处理人列表支持的条件，屏蔽其他筛选字段，保留原始请求对象。
     *
     * @param source 待转换或读取的源数据
     * @param handlerId 处理人用户 ID
     * @return 工单搜索查询条件
     */
    private TicketSearchQuery normalizeQuery(TicketSearchQuery source, Long handlerId) {
        TicketSearchQuery target = new TicketSearchQuery();
        target.setKeyword(trimToNull(source.getKeyword()));
        String status = trimToNull(source.getStatus());
        target.setStatus("all".equalsIgnoreCase(status) ? null : status);
        String sort = trimToNull(source.getSort());
        target.setSort(sort == null ? "deadline" : sort);
        target.setPage(source.getPage());
        target.setPageSize(source.getPageSize());
        // 身份筛选始终覆盖为已认证处理人，不能让请求中的 handlerId 扩大可查询范围。
        target.setHandlerId(handlerId.toString());
        return target;
    }

    /**
     * 去除首尾空白，将空字符串统一转为不限制条件。
     *
     * @param value 待处理的值
     * @return 本次处理得到的文本
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
