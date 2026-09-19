package com.WorkOrder.sla.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author Virgor
 * @date 2026年09月18日 20:35
 * @description
 */
@FeignClient(name = "ticket-service", path = "/tickets", configuration = SlaMonitorFeignConfiguration.class)
public interface TicketFeignClient {
    @PostMapping("/sla-status")
    Result<PageResult<TicketResponse>> getTicketsBySlaStatus(
            @RequestParam("page") Long page,
            @RequestParam("pageSize") Long pageSize,
            @RequestParam(value = "slaStatus", required = false) String slaStatus,
            @RequestParam(value = "status", required = false) String status);
}
