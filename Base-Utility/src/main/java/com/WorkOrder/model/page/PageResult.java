package com.WorkOrder.model.page;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月08日 19:07
 * @description 分页结果
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {
    // 分页数据
    private List<T> list;
    // 总条数
    private long total;
    // 当前页码
    private long page;
    // 每页大小
    private long pageSize;

    /**
     * 构造分页查询结果。
     *
     * @param list 当前页的数据列表
     * @param total 符合条件的数据总条数
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     */
    public PageResult(List<T> list, long total, long page, long pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
