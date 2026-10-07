package com.WorkOrder.model.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 处理人技能响应项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandlerSkillItem {

    /**
     * 技能标签 ID。
     */
    private Long skillId;

    /**
     * 技能名称。
     */
    private String skillName;

    /**
     * 熟练度，取值范围 1-5。
     */
    private int proficiency;
}
