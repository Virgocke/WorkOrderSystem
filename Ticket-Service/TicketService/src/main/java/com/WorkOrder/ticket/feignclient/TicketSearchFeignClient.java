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
@FeignClient(name = "Search", path = "/internal/search/", configuration = TicketSearchFeignConfiguration.class)
public interface TicketSearchFeignClient {

    /**
     * 向搜索服务查询工单搜索结果。
     *
     * @param query 已约束角色与业务筛选条件的工单查询请求
     * @return 搜索服务返回的候选工单及分页信息
     * @throws IOException 处理过程中发生IO异常时
     */
    @PostMapping("/tickets/admin")
    Result<TicketSearchPage> esSearch(@RequestBody TicketSearchQuery query) throws IOException;

    /**
     * 透传当前处理人的 Bearer Token，由 Search 服务重新生成处理人权限条件。
     *
     * @param query 工单搜索查询条件
     * @return 统一响应，包含工单搜索分页
     * @throws IOException 处理过程中发生IO异常时
     */
    @PostMapping("/tickets/handler")
    Result<TicketSearchPage> esSearchForHandler(@RequestBody TicketSearchQuery query) throws IOException;
}
