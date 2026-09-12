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
