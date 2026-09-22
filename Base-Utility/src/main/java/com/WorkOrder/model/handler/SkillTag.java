package com.WorkOrder.model.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能标签列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTag {

    /** 技能标签 ID。 */
    private Long id;

    /** 技能名称。 */
    private String name;

    /** 技能说明。 */
    private String description;

    /** 当前配置了该技能的处理人数。 */
    private Integer usedCount;
}
