package com.WorkOrder.controller;

import com.WorkOrder.handler.dto.HandlerPageListDto;
import com.WorkOrder.handler.dto.UpdateHandlerProfileRequest;
import com.WorkOrder.handler.dto.UpdateHandlerSkillsRequest;
import com.WorkOrder.handler.service.HandlerUserService;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 02:29
 * @description 处理人控制器
 */
@RestController
@RequestMapping("/handlers")
@RequiredArgsConstructor
public class HandlerController {

    private final HandlerUserService handlerService;

    /**
     * 获取可供当前接口查询的处理人列表。
     *
     * @return 处理人资料列表的统一响应
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/options")
    public Result<List<HandlerProfile>> getHandler() {
        return Result.success(handlerService.getHandler());
    }

    /**
     * 分页查询处理人档案，支持姓名、账号、技能关键字及状态筛选。
     *
     * @param query 分页与筛选条件
     * @return 处理人分页结果
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<PageResult<HandlerProfile>> listHandlers(@Valid HandlerPageListDto query) {
        return Result.success(handlerService.listHandlers(query));
    }

    /**
     * 查询全部处理人档案（包含停用账号）。
     *
     * @return 全部处理人档案
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public Result<List<HandlerProfile>> listAllHandlers() {
        return Result.success(handlerService.listAllHandlers());
    }

    /**
     * 按处理人的用户 ID 部分更新容量、部门和启停状态。
     *
     * @param id 处理人用户 ID，不是处理人档案主键
     * @param request 待更新字段
     * @return 更新后的处理人档案
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<HandlerProfile> updateHandler(@PathVariable Long id,
                                                @Valid @RequestBody UpdateHandlerProfileRequest request) {
        return Result.success(handlerService.updateHandler(id, request));
    }

    /**
     * 按处理人的用户 ID 全量覆盖技能配置。
     *
     * @param id 处理人用户 ID
     * @param request 完整技能列表，空列表表示清空
     * @return 更新后的处理人档案
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/skills")
    public Result<HandlerProfile> updateHandlerSkills(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHandlerSkillsRequest request) {
        return Result.success(handlerService.updateHandlerSkills(id, request));
    }
}
