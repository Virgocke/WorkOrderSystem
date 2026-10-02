package com.WorkOrder.search.service;

import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.repository.TicketSearchRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * @author Virgor
 * @date 2026年10月03日 04:16
 * @description 管理员工单搜索服务
 */
@ConditionalOnProperty(
        prefix = "work-order.elasticsearch",
        name = "enabled",
        havingValue = "true"
)
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketSearchService {

    private final TicketSearchRepository ticketSearchRepository;

    /**
     * 创建管理员工单搜索服务，仅保存依赖，不主动连接节点或创建索引。
     * @param ticketSearchRepository 工单搜索仓库
     */
    public AdminTicketSearchService(TicketSearchRepository ticketSearchRepository) {
        this.ticketSearchRepository = ticketSearchRepository;
    }

    /**
     * 复制并规范化管理员搜索条件，校验后交由仓库查询，不修改原始请求。
     *
     * @param source 管理员传入的搜索条件
     * @return ES 投影的当前页及准确命中总数
     * @throws IllegalArgumentException 条件为空、优先级或仓库查询参数不合法
     * @throws IOException ES 请求失败或未返回完整结果
     */
    public TicketSearchPage search(TicketSearchQuery source) throws IOException {
        // 对查询参数进行标准化处理
        TicketSearchQuery normalizedQuery = normalizeQuery(source);
        // 校验业务筛选值；分页和关键字长度由仓库统一校验。
        validateQuery(normalizedQuery);

        return ticketSearchRepository.search(normalizedQuery);
    }

    /** 复制请求，将空白、all 状态及优先级 0 转换为不限制对应条件。 */
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
     * @param query 查询参数
     */
    private void validateQuery(TicketSearchQuery query) {
        Integer priority = query.getPriority();
        Assert.isTrue(priority == null || priority >= 1 && priority <= 4,
                "优先级必须介于 1 和 4 之间");
    }

    /**
     * 去除字符串两端的空格，如果结果为空则返回null
     * @param value 要转换的值
     * @return 若值为空或仅包含空格则返回null，否则返回去除两端空格的字符串
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
