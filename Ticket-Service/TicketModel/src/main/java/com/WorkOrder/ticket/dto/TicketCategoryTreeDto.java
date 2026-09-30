package com.WorkOrder.ticket.dto;

import com.WorkOrder.ticket.model.TicketCategory;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 03:47
 * @description 工单分类树DTO
 */
@Data
public class TicketCategoryTreeDto {
    private Long id;
    private String name;
    private Long parentId;
    private int defaultPriority;
    private int defaultResponseSla;
    private int defaultResolutionSla;
    private String description;
    /** 当前分类直接配置的技能标签 ID；空列表表示继承最近祖先分类。 */
    private List<Long> requiredSkillIds = new ArrayList<>();
    private List<TicketCategoryTreeDto> children = new ArrayList<>();

    public static TicketCategoryTreeDto from(TicketCategory entity) {
        TicketCategoryTreeDto response = new TicketCategoryTreeDto();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setParentId(entity.getParentId());
        response.setDefaultPriority(entity.getDefaultPriority());
        response.setDefaultResponseSla(entity.getDefaultResponseSla());
        response.setDefaultResolutionSla(entity.getDefaultResolutionSla());
        response.setDescription(entity.getDescription());
        return response;
    }
}
