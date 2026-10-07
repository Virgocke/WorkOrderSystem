package com.WorkOrder.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 按顶级工单分类聚合的报表数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryReportDto {

    /**
     * 顶级分类 ID。
     */
    private Long categoryId;

    /**
     * 顶级分类名称。
     */
    private String categoryName;

    /**
     * 顶级分类及其所有后代分类下的工单总数。
     */
    private long count;

    /**
     * 分类内有效解决分钟总和除以全部工单数，未解决或异常时间按零计。
     */
    private BigDecimal avgResolutionMinutes;
}
