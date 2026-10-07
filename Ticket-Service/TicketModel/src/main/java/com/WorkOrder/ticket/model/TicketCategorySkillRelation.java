package com.WorkOrder.ticket.model;

import lombok.Data;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 分类与所需技能的只读关系行。
 */
@Data
public class TicketCategorySkillRelation {
    /**
     * 工单分类 ID。
     */
    private Long categoryId;
    /**
     * 所需技能标签 ID。
     */
    private Long skillTagId;
}
