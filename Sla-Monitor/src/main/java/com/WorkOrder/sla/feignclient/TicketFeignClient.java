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
 * @description 调用工单服务，读取当前模块所需的工单信息。
 */
@FeignClient(name = "ticket-service", path = "/tickets", configuration = SlaMonitorFeignConfiguration.class)
public interface TicketFeignClient {
    /**
     * 调用工单服务，按 SLA 状态和工单状态分页查询工单。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param slaStatus SLA 状态筛选条件
     * @param status 工单状态筛选条件
     * @return 符合条件的工单分页响应
     */
    @PostMapping("/sla-status")
    Result<PageResult<TicketResponse>> getTicketsBySlaStatus(
            @RequestParam("page") Long page,
            @RequestParam("pageSize") Long pageSize,
            @RequestParam(value = "slaStatus", required = false) String slaStatus,
            @RequestParam(value = "status", required = false) String status);
}
