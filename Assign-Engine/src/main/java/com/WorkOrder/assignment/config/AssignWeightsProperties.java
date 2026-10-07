package com.WorkOrder.assignment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 尚未保存数据库配置时的默认分配权重，默认技能40、负载25、SLA20、评分15。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "assignment.weights")
public class AssignWeightsProperties {

    @NotNull
    @DecimalMin("0")
    private BigDecimal skill = new BigDecimal("40");

    @NotNull
    @DecimalMin("0")
    private BigDecimal load = new BigDecimal("25");

    @NotNull
    @DecimalMin("0")
    private BigDecimal sla = new BigDecimal("20");

    @NotNull
    @DecimalMin("0")
    private BigDecimal rating = new BigDecimal("15");

    /**
     * 权重总和必须大于零，避免计算综合分时除零。
     *
     * @return 是否满足校验条件
     */
    @AssertTrue(message = "分配权重总和必须大于零")
    public boolean isTotalWeightPositive() {
        return skill != null && load != null && sla != null && rating != null
                && skill.add(load).add(sla).add(rating).signum() > 0;
    }
}
