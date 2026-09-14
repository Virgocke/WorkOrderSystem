package com.WorkOrder.ticket.controller;

import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.StatusHistory;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.dto.MyTicketPageDto;
import com.WorkOrder.ticket.dto.TicketHistoryStatisticsDto;
import com.WorkOrder.ticket.service.TicketService;
import com.WorkOrder.ticket.service.TicketStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 03:24
 * @description 工单控制类
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketStatusHistoryService ticketStatusHistoryService;
    private final CurrentUserIdProvider currentUserIdProvider;


    /**
     * 创建工单
     * @param createTicketDto 创建工单的DTO
     * @param authentication 当前用户认证信息
     * @return 创建的工单信息
     */
    @PostMapping
    public Result<TicketResponse> createTicket(
                            @Valid @RequestBody CreateTicketDto createTicketDto,
                                               Authentication authentication) {
        Long creatorId = currentUserIdProvider.get(authentication);
        String creatorName = authentication.getName();
        return Result.success(ticketService.createTicket(creatorId, creatorName, createTicketDto));
    }

    /**
     * 获取当前用户工单列表
     * @param authentication 当前用户认证信息
     * @param myTicketPageDto 工单分页查询DTO
     * @return 工单列表
     */
    @GetMapping("/mine")
    public Result<PageResult<TicketResponse>> myTickets(Authentication authentication,
                                                        MyTicketPageDto myTicketPageDto){
        List<TicketResponse> ticketList = ticketService.myTickets(currentUserIdProvider.get(authentication), myTicketPageDto);

        return Result.success(
                new PageResult<>(
                        ticketList,
                        ticketList.size(),
                        myTicketPageDto.getPage(),
                        myTicketPageDto.getPageSize()
                ));
    }

    /**
     * 获取当前用户工单统计信息
      * @param authentication 当前用户认证信息
      * @return 工单统计信息
     */
    @GetMapping("/me/stats")
    public Result<TicketHistoryStatisticsDto> ticketHistoryStatistics(Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(ticketService.getTicketHistoryStatistics(userId));
    }

    /**
     * 获取工单信息
     * @param ticketId 工单ID
     * @return 工单信息
     */
    @PreAuthorize(
            "hasRole('ADMIN') or @ticketAuthorization.canView(#ticketId, authentication)"
    )
    @GetMapping("/{id}")
    public Result<TicketResponse> getTicketInfo(@P("ticketId") @PathVariable("id") Long ticketId){
        return Result.success(ticketService.getTicketInfo(ticketId));
    }

    @GetMapping("/{id}/history")
    public Result<List<StatusHistory>> ticketStatusTimeline(@PathVariable("id") Long ticketId){
        return Result.success(ticketStatusHistoryService.getTicketStatusTimeline(ticketId));
    }
}
