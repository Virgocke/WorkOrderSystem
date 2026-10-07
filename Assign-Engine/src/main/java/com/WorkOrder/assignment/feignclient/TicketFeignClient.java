package com.WorkOrder.assignment.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.TicketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author Virgor
 * @date 2026年09月18日 20:35
 * @description 调用工单服务，读取当前模块所需的工单信息。
 */
@FeignClient(name = "ticket-service", path = "/tickets", configuration = AssignmentFeignConfiguration.class)
public interface TicketFeignClient {
    /**
     * 按工单 ID 读取工单详情。
     *
     * @param ticketId 工单 ID
     * @return 工单详情的统一响应
     */
    @GetMapping("/{id}")
    Result<TicketResponse> getTicketInfo(@PathVariable("id") Long ticketId);
}
