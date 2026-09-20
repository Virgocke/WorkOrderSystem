package com.WorkOrder.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author Virgor
 * @date 2026年09月20日 19:22
 * @description 处理人绩效DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HandlerReportPerformanceDto {
    private Long handlerId;
    private String realName;
    private String departmentName;
    private long assignedCount;
    private long resolvedCount;
    private BigDecimal avgResponseMinutes;
    private BigDecimal avgResolutionMinutes;
    private BigDecimal slaComplianceRate;
    private BigDecimal ratingScore;
}
