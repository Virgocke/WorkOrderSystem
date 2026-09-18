package com.WorkOrder.assignment.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.TicketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author Virgor
 * @date 2026年09月18日 20:35
 * @description
 */
@FeignClient(name = "ticket-service", path = "/tickets", configuration = AssignmentFeignConfiguration.class)
public interface TicketFeignClient {
    @GetMapping("/{id}")
    Result<TicketResponse> getTicketInfo(@PathVariable("id") Long ticketId);
}
