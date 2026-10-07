package com.WorkOrder.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 处理人工作台汇总查询结果。
 */
@Data
public class HandlerWorkbenchSummaryDto {

    /**
     * 待首次响应的工单数量。
     */
    private long pendingCount;

    /**
     * 即将超时、已超时或已升级的未终结工单数量。
     */
    private long nearTimeoutCount;

    /**
     * 今天解决或关闭的工单数量。
     */
    private long doneToday;

    /**
     * 从工单创建到解决的历史平均时长，单位为分钟。
     */
    private BigDecimal avgResolutionMinutes;
}
