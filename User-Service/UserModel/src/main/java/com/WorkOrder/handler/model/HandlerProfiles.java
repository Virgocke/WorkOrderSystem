package com.WorkOrder.handler.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 自动派单所需的处理人基础信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("handler_profiles")
public class HandlerProfiles {
    private Long id;
    private Long userId;
    private int maxCapacity;
    private int currentLoad;
    private int avgResponseMinutes;
    private int avgResolutionMinutes;
    private BigDecimal slaComplianceRate;
    private BigDecimal ratingScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
