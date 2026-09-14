package com.WorkOrder.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月08日 19:07
 * @description 分页结果
 */
@Data
public class PageResult<T> implements Serializable {
    // 分页数据
    private List<T> list;
    // 总条数
    private long total;
    // 当前页码
    private long page;
    // 每页大小
    private long pageSize;

    public PageResult(List<T> list, long total, long page, long pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
