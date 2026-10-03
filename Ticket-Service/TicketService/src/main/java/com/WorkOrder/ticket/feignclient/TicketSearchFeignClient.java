package com.WorkOrder.ticket.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;

/**
 * @author Virgor
 * @date 2026年10月03日 04:45
 * @description ES查询工单服务
 */
@FeignClient(name = "Search", path = "/internal/search/" , configuration = TicketFeignConfiguration.class)
public interface TicketSearchFeignClient {

    @PostMapping("/tickets/admin")
    Result<TicketSearchPage> esSearch(@RequestBody TicketSearchQuery query) throws IOException;

    /** 透传当前处理人的 Bearer Token，由 Search 服务重新生成处理人权限条件。 */
    @PostMapping("/tickets/handler")
    Result<TicketSearchPage> esSearchForHandler(@RequestBody TicketSearchQuery query) throws IOException;
}
