package com.WorkOrder.search.dto;

import com.WorkOrder.search.document.TicketSearchDocument;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 搜索内部结果，后续业务层负责授权复核和转换接口响应。 */
@Getter
public class TicketSearchPage {

    /** 当前页的搜索投影列表；复制列表结构后禁止增删，但其中的文档对象仍可修改。 */
    private final List<TicketSearchDocument> records;

    /** 满足查询条件的准确总数，统计范围为 Elasticsearch 搜索投影。 */
    private final long total;

    /** 当前页码，从 1 开始。 */
    private final int page;

    /** 本次请求的每页数量。 */
    private final int pageSize;

    /**
     * 根据搜索结果构建分页对象，并复制记录列表，避免调用方修改原列表影响页结构。
     *
     * @param records 当前页的搜索投影记录，不能为 null
     * @param total Elasticsearch 返回的准确命中总数
     * @param page 已通过仓库校验的页码，从 1 开始
     * @param pageSize 已通过仓库校验的每页数量
     */
    public TicketSearchPage(List<TicketSearchDocument> records, long total, int page, int pageSize) {
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
