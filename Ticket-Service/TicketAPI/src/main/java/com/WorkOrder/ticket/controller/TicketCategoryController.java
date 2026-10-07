package com.WorkOrder.ticket.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.ticket.dto.TicketCategoryDto;
import com.WorkOrder.ticket.dto.TicketCategoryTreeDto;
import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 03:22
 * @description 工单分类树控制器，用于获取工单类别树、创建工单类别、更新工单类别、删除工单类别
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/ticket-categories")
public class TicketCategoryController {

    private final TicketCategoryService ticketCategoryService;

    /**
     * 获取工单类别树
     *
     * @return 工单类别树
     */
    @GetMapping("/tree")
    public Result<List<TicketCategoryTreeDto>> getTicketCategoryTree(){
        return Result.success(ticketCategoryService.getTicketCategoryTree());
    }

    /**
     * 创建工单类别
     *
     * @param ticketCategoryDto 工单分类请求数据
     * @return 创建的工单类别
     */
    @PreAuthorize("hasAuthority('category:manage')")
    @PostMapping
    public Result<TicketCategory> createTicketCategory(@Valid @RequestBody TicketCategoryDto ticketCategoryDto){
        return Result.success(ticketCategoryService.createTicketCategory(ticketCategoryDto));
    }

    /**
     * 更新工单类别
     *
     * @param id 工单 ID
     * @param ticketCategoryDto 工单分类请求数据
     * @return 更新的工单类别
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<TicketCategory> updateTicketCategory(@PathVariable Long id, @Valid @RequestBody TicketCategoryDto ticketCategoryDto){
        return Result.success(ticketCategoryService.updateTicketCategory(id, ticketCategoryDto));
    }

    /**
     * 删除工单类别
     *
     * @param id 工单 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<Boolean> deleteTicketCategory(@PathVariable Long id){
        return Result.success(ticketCategoryService.deleteTicketCategory(id));
    }
}
