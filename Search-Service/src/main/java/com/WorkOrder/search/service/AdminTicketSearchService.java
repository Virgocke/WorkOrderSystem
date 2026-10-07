package com.WorkOrder.search.service;

import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.repository.TicketSearchRepository;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import org.elasticsearch.ElasticsearchStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * @author Virgor
 * @date 2026年10月03日 04:16
 * @description 管理员工单搜索服务
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminTicketSearchService.class);

    private final Optional<TicketSearchRepository> ticketSearchRepository;
    private final TicketSearchReadinessService readinessService;

    /**
     * 创建管理员工单搜索服务，仅保存依赖，不主动连接节点或创建索引。
     *
     * @param ticketSearchRepository ES 关闭时允许缺失的工单搜索仓库
     * @param readinessService 基于主库及真实别名的查询就绪门禁
     */
    public AdminTicketSearchService(Optional<TicketSearchRepository> ticketSearchRepository,
                                    TicketSearchReadinessService readinessService) {
        this.ticketSearchRepository = ticketSearchRepository;
        this.readinessService = readinessService;
    }

    /**
     * 复制并规范化管理员搜索条件，校验后交由仓库查询，不修改原始请求。
     *
     * @param source 管理员传入的搜索条件
     * @return ES 投影的当前页及准确命中总数
     * @throws IllegalArgumentException 条件为空、优先级或仓库查询参数不合法
     * @throws SystemException 同步未就绪、ES 查询失败或查询期间已发布目标发生变化
     * @throws IOException 处理过程中发生IO异常时
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TicketSearchPage search(TicketSearchQuery source) throws IOException {
        // 对查询参数进行标准化处理
        TicketSearchQuery normalizedQuery = normalizeQuery(source);
        // 校验业务筛选值；分页和关键字长度由仓库统一校验。
        validateQuery(normalizedQuery);

        // 记录查询开始时的已发布代次，重建或未完成发布时直接拒绝关键词搜索。
        TicketSearchWriteTarget target = readinessService.requireReady();
        TicketSearchRepository repository = ticketSearchRepository.orElseThrow(
                () -> new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY));
        TicketSearchPage result;
        try {
            result = repository.search(normalizedQuery);
        } catch (IOException | ElasticsearchStatusException exception) {
            // 对调用方统一表达“搜索暂不可用”，保留原异常便于排查，避免泄漏 ES 响应正文。
            LOGGER.warn("管理员工单关键词查询不可用，generation={}，failureType={}",
                    target.getGeneration(), exception.getClass().getSimpleName());
            SystemException unavailable = new SystemException(SystemExceptionEnum.TICKET_SEARCH_NOT_READY);
            unavailable.initCause(exception);
            throw unavailable;
        }
        // ES 查询期间可能已经启动重建或切换代次，返回前再次确认，避免交出跨代次结果。
        readinessService.assertStillReady(target);
        return result;
    }

    /**
     * 复制请求，将空白、all 状态及优先级 0 转换为不限制对应条件。
     *
     * @param source 待转换或读取的源数据
     * @return 工单搜索查询条件
     */
    private TicketSearchQuery normalizeQuery(TicketSearchQuery source) {

        Assert.notNull(source, "搜索条件不能为空");
        // 复制所有属性，保留原始请求对象
        TicketSearchQuery target = new TicketSearchQuery();
        BeanUtils.copyProperties(source, target);

        target.setKeyword(trimToNull(target.getKeyword()));
        target.setCategoryId(trimToNull(target.getCategoryId()));
        target.setCreatorId(trimToNull(target.getCreatorId()));
        target.setHandlerId(trimToNull(target.getHandlerId()));
        target.setSlaStatus(trimToNull(target.getSlaStatus()));

        // 将 all 和空白统一转换为“不限制状态”。
        String status = trimToNull(target.getStatus());
        target.setStatus("all".equalsIgnoreCase(status) ? null : status);

        // 兼容现有管理员列表用 0 表示“不限制优先级”
        Integer priority = target.getPriority();
        if (priority != null && priority == 0) {
            target.setPriority(null);
        }

        return target;
    }

    /**
     * 验证查询参数
     *
     * @param query 查询参数
     */
    private void validateQuery(TicketSearchQuery query) {
        Integer priority = query.getPriority();
        Assert.isTrue(priority == null || priority >= 1 && priority <= 4,
                "优先级必须介于 1 和 4 之间");
    }

    /**
     * 去除字符串两端的空格，如果结果为空则返回null
     *
     * @param value 要转换的值
     * @return 若值为空或仅包含空格则返回null，否则返回去除两端空格的字符串
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
