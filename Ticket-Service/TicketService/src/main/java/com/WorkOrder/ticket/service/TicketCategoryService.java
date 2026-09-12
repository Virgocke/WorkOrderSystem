package com.WorkOrder.ticket.service;

import com.WorkOrder.ticket.dto.TicketCategoryDto;
import com.WorkOrder.ticket.dto.TicketCategoryTreeDto;
import com.WorkOrder.ticket.model.TicketCategory;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 03:43
 * @description
 */
public interface TicketCategoryService {
    /**
     * 获取工单类别树
     * @return 工单类别树
     */
    List<TicketCategoryTreeDto> getTicketCategoryTree();

    /**
     * 创建工单类别
     * @param ticketCategoryDto
     * @return 创建的工单类别
     */
    TicketCategory createTicketCategory(TicketCategoryDto ticketCategoryDto);
}
