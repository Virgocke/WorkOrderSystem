package com.WorkOrder.dashboard.dto;

import lombok.Data;

/** 数据库分组统计结果，名称由服务层转换为前端展示文案。 */
@Data
public class DashboardGroupCountDto {

    /** 数据库存储的状态或优先级分组键。 */
    private String groupKey;

    /** 当前分组的工单数量。 */
    private long count;
}
