package com.WorkOrder.user.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/** 自动派单所需的处理人基础信息。 */
@Data
@NoArgsConstructor
public class HandlerProfile {
    private Integer maxCapacity;
    private Set<String> skills = new LinkedHashSet<>();

    /**
     * 使用容量和技能创建处理人档案，并防御性复制技能集合。
     *
     * @param maxCapacity 最大同时处理工单数
     * @param skills 技能标签集合，可为空
     */
    public HandlerProfile(Integer maxCapacity, Set<String> skills) {
        this.maxCapacity = maxCapacity;
        if (skills != null) {
            this.skills = new LinkedHashSet<>(skills);
        }
    }
}
