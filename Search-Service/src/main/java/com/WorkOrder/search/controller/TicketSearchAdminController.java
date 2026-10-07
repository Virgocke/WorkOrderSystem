package com.WorkOrder.search.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.search.model.*;
import com.WorkOrder.search.model.admin.*;
import com.WorkOrder.search.service.TicketSearchImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.io.IOException;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单索引运维接口；internal 路径不能替代 ADMIN 鉴权。
 */
@RestController
@RequestMapping("/internal/search/tickets/index")
@PreAuthorize("hasRole('ADMIN')")
public class TicketSearchAdminController {
    private final TicketSearchImportService service;

    /**
     * 注入独立管理服务。
     *
     * @param service 服务
     */
    public TicketSearchAdminController(TicketSearchImportService service) { this.service=service; }

    /**
     * 当前共享进度、就绪检查与消息积压。
     *
     * @return 统一响应，包含工单搜索管理员状态
     */
    @GetMapping("/status")
    public Result<TicketSearchAdminStatus> status() { return Result.success(service.status()); }

    /**
     * 登记异步导入任务，不等待全量扫描完成。
     *
     * @param request 工单搜索Create导入请求
     * @return 统一响应，包含工单索引导入任务
     */
    @PostMapping("/imports")
    public Result<TicketSearchImportTask> create(@Valid @RequestBody TicketSearchCreateImportRequest request) {
        return Result.success(service.create(request));
    }

    /**
     * 指定任务的持久进度和错误分类。
     *
     * @param id 工单 ID
     * @return 统一响应，包含工单索引导入任务
     */
    @GetMapping("/imports/{id}")
    public Result<TicketSearchImportTask> task(@PathVariable Long id) { return Result.success(service.task(id)); }

    /**
     * 续跑或恢复同一任务。
     *
     * @param id 工单 ID
     * @param request 工单搜索Resume请求
     * @return 统一响应，包含工单索引导入任务
     * @throws IOException 处理过程中发生IO异常时
     */
    @PostMapping("/imports/{id}/resume")
    public Result<TicketSearchImportTask> resume(@PathVariable Long id,
            @Valid @RequestBody(required=false) TicketSearchResumeRequest request) throws IOException {
        return Result.success(service.resume(id,request));
    }

    /**
     * 完整验证后独立发布。
     *
     * @param id 工单 ID
     * @param request 工单搜索发布请求
     * @return 统一响应，包含工单索引导入任务
     * @throws IOException 处理过程中发生IO异常时
     */
    @PostMapping("/imports/{id}/publish")
    public Result<TicketSearchImportTask> publish(@PathVariable Long id,
            @Valid @RequestBody TicketSearchPublishRequest request) throws IOException {
        return Result.success(service.publish(id,request));
    }

    /**
     * 以源版本修复当前代次的一张工单。
     *
     * @param ticketId 工单 ID
     * @return 统一响应，包含工单搜索写入Outcome
     * @throws IOException 处理过程中发生IO异常时
     */
    @PostMapping("/repairs/{ticketId}")
    public Result<TicketSearchWriteOutcome> repair(@PathVariable Long ticketId) throws IOException {
        return Result.success(service.repair(ticketId));
    }
}
