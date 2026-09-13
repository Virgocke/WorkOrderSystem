package com.WorkOrder.ticket.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.TicketResponse;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

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
    private final CurrentUserIdProvider currentUserIdProvider;


    @PostMapping
    public Result<TicketResponse> createTicket(
                            @Valid @RequestBody CreateTicketDto createTicketDto,
                                               Authentication authentication) {
        Long creatorId = currentUserIdProvider.get(authentication);
        String creatorName = authentication.getName();
        return Result.success(ticketService.createTicket(creatorId, creatorName, createTicketDto));
    }
}
