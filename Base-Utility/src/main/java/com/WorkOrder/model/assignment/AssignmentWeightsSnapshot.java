package com.WorkOrder.model.assignment;

import lombok.Value;

import java.math.BigDecimal;

/** 一次推荐使用的不可变权重快照，版本0表示使用应用默认配置。 */
@Value
public class AssignmentWeightsSnapshot {
    BigDecimal skill;
    BigDecimal load;
    BigDecimal sla;
    BigDecimal rating;
    Long version;
}
