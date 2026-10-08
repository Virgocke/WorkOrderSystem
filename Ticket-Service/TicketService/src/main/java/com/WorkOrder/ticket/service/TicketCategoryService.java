package com.WorkOrder.ticket.service;

import com.WorkOrder.ticket.dto.TicketCategoryDto;
import com.WorkOrder.ticket.dto.TicketCategoryTreeDto;
import com.WorkOrder.ticket.model.TicketCategory;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 03:43
 * @description 管理工单分类及其分类树。
 */
public interface TicketCategoryService {
    /**
     * 获取工单类别树
     *
     * @return 工单类别树
     */
    List<TicketCategoryTreeDto> getTicketCategoryTree();

    /**
     * 返回所选分类及其所有后代，供工单列表统一筛选。
     *
     * @param categoryId 所选分类 ID
     * @return 去重后的分类 ID，始终包含所选分类本身
     */
    List<Long> getCategoryIdsInSubtree(Long categoryId);

    /**
     * 创建工单类别
     *
     * @param ticketCategoryDto 工单分类请求数据
     * @return 创建的工单类别
     */
    TicketCategory createTicketCategory(TicketCategoryDto ticketCategoryDto);

    /**
     * 更新工单类别
     *
     * @param id 工单 ID
     * @param ticketCategoryDto 工单分类请求数据
     * @return 更新的工单类别
     */
    TicketCategory updateTicketCategory(Long id, TicketCategoryDto ticketCategoryDto);

    /**
     * 删除工单类别
     *
     * @param id 工单 ID
     * @return 删除结果
     */
    Boolean deleteTicketCategory(Long id);
}
