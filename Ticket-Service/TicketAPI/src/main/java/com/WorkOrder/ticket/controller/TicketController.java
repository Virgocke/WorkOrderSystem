package com.WorkOrder.ticket.controller;

import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.handler.service.HandlerTicketService;
import com.WorkOrder.model.ticket.OperationLog;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.StatusHistory;
import com.WorkOrder.model.ticket.TicketRatingResponse;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.ticket.dto.*;
import com.WorkOrder.ticket.service.TicketOperationLogService;
import com.WorkOrder.ticket.service.TicketRatingService;
import com.WorkOrder.ticket.service.TicketService;
import com.WorkOrder.ticket.service.TicketStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

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
    private final TicketOperationLogService ticketOperationLogService;
    private final TicketRatingService ticketRatingService;
    private final HandlerTicketService handlerTicketService;


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

    /**
     * 获取工单状态时间线
     * @param ticketId 工单ID
     * @return 工单状态时间线
     */
    @PreAuthorize(
        "hasRole('ADMIN') or @ticketAuthorization.canView(#ticketId, authentication)"
    )
    @GetMapping("/{id}/history")
    public Result<List<StatusHistory>> ticketStatusTimeline(@P("ticketId") @PathVariable("id") Long ticketId){
        return Result.success(ticketStatusHistoryService.getTicketStatusTimeline(ticketId));
    }

    /**
     * 获取工单操作日志
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @return 工单操作日志
     */
    @PreAuthorize(
        "hasRole('ADMIN') or @ticketAuthorization.canView(#ticketId, authentication)"
    )
    @GetMapping("/{id}/logs")
    public Result<List<OperationLog>> ticketOperationLog(
            @P("ticketId") @PathVariable("id") Long ticketId,
            Authentication authentication){

        List<OperationLog> logs = ticketOperationLogService.getTicketOperationLog(ticketId);

        boolean isNormalUser = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_USER".equals(authority.getAuthority()));

        if (isNormalUser){
            logs = logs.stream()
                    .filter(log -> !"INTERNAL_NOTE".equals(log.getAction()))
                    .collect(Collectors.toList());
        }

        return Result.success(logs);
    }

    /**
     * 获取工单评分
      * @param ticketId 工单ID
      * @param authentication 当前用户认证信息
      * @return 工单评分
     */
    @PreAuthorize(
        "hasRole('ADMIN') or @ticketAuthorization.canView(#ticketId, authentication)"
    )
    @GetMapping("/{id}/rating")
    public Result<TicketRatingResponse> ticketRating(@P("ticketId") @PathVariable("id") Long ticketId, Authentication authentication){
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(ticketRatingService.getTicketRating(ticketId, userId));
    }

    /**
     * 获取工单回复信息
      * @param ticketId 工单ID
      * @param authentication 当前用户认证信息
      * @param ticketReplyDto 工单回复信息DTO
      * @return 工单回复信息
     */
    @PreAuthorize(
            "hasRole('ADMIN') or @ticketAuthorization.canView(#ticketId, authentication)"
    )
    @PostMapping("/{id}/reply")
    public Result<Boolean> ticketReplyInfo(
            @P("ticketId") @PathVariable("id") Long ticketId,
            Authentication authentication,
            @Valid @RequestBody TicketReplyDto ticketReplyDto){

        Long userId = currentUserIdProvider.get(authentication);
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return Result.success(ticketService.ticketReplyInfo(ticketId, userId, isAdmin, ticketReplyDto));
    }

    /**
     * 工单催办
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @return 是否成功
     */
    @PreAuthorize(
            "@ticketAuthorization.isCreator(#ticketId, authentication)"
    )
    @PostMapping("/{id}/remind")
    public Result<Boolean> ticketExpedite(@P("ticketId") @PathVariable("id") Long ticketId,Authentication authentication){
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(ticketService.ticketExpedite(ticketId, userId));
    }

    /**
     * 撤销工单
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @return 取消后的工单信息
     */
    @PreAuthorize(
            "hasRole('ADMIN') or @ticketAuthorization.isCreator(#ticketId, authentication)"
    )
    @PostMapping("/{id}/cancel")
    public Result<TicketResponse> cancelTicket(@P("ticketId") @PathVariable("id") Long ticketId, Authentication authentication){
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(ticketService.cancelTicket(ticketId, userId));
    }

    /**
     * 确认工单
      * @param ticketId 工单ID
      * @param authentication 当前用户认证信息
      * @return 确认后的工单信息
     */
    @PreAuthorize(
            "hasRole('ADMIN') or @ticketAuthorization.isCreator(#ticketId, authentication)"
    )
    @PostMapping("/{id}/confirm")
    public Result<TicketResponse> confirmTicket(@P("ticketId") @PathVariable("id") Long ticketId, Authentication authentication){
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(ticketService.confirmTicket(ticketId, userId));
    }

    /**
     * 提交工单评分
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @param ticketRatingDto 工单评分DTO
     * @return 是否成功
     */
    @PreAuthorize(
            "@ticketAuthorization.isCreator(#ticketId, authentication)"
    )
    @PostMapping("/{id}/rate")
    public Result<Boolean> ticketRatingSubmit(
            @P("ticketId") @PathVariable("id") Long ticketId,
            Authentication authentication,
            @Valid @RequestBody TicketRatingDto ticketRatingDto){
        Long userId = currentUserIdProvider.get(authentication);
        return Result.success(ticketRatingService.ticketRatingSubmit(ticketId, userId, ticketRatingDto));
    }


    @GetMapping("/handler")
    public PageResult<TicketResponse> getTicketList(
            Authentication authentication,
            @Valid HandlerTicketPageDto handlerTicketPageDto){

        Long handlerId = currentUserIdProvider.get(authentication);
        List<TicketResponse> ticketList = handlerTicketService.getHandlerTicket(handlerId, handlerTicketPageDto);

        return new PageResult<>(
                ticketList,
                ticketList.size(),
                handlerTicketPageDto.getPage(),
                handlerTicketPageDto.getPageSize()
                );
    }
}
