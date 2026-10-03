package com.WorkOrder.search.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.search.TicketSearchPage;
import com.WorkOrder.model.search.TicketSearchQuery;
import com.WorkOrder.search.service.AdminTicketSearchService;
import com.WorkOrder.search.service.HandlerTicketSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * @author Virgor
 * @date 2026年10月03日 04:00
 * @description ES内部搜索控制器
 */
@RestController
@RequestMapping("/internal/search/")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "work-order.elasticsearch",
        name = "enabled",
        havingValue = "true"
)
public class ElasticsearchSearchController {

    private final AdminTicketSearchService adminTicketSearchService;

    private final HandlerTicketSearchService handlerTicketSearchService;

    /** 查询通过管理员方法权限校验的工单搜索页。 */
    @PostMapping("/tickets/admin")
    public Result<TicketSearchPage> esSearch(@RequestBody TicketSearchQuery query) throws IOException {
        return Result.success(adminTicketSearchService.search(query));
    }

    /** 搜索当前登录处理人的工单；处理人身份由服务从已认证的令牌生成。 */
    @PostMapping("/tickets/handler")
    public Result<TicketSearchPage> searchHandlerTickets(@RequestBody TicketSearchQuery query) throws IOException {
        return Result.success(handlerTicketSearchService.search(query));
    }
}
