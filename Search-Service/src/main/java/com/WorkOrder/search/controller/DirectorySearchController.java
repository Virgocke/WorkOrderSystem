package com.WorkOrder.search.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.search.service.DirectorySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** 目录搜索内部接口，搜索字段由服务端选择，权限由搜索服务方法再次校验。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/search/directory")
@ConditionalOnProperty(prefix = "work-order.elasticsearch", name = "enabled", havingValue = "true")
public class DirectorySearchController {
    private final DirectorySearchService service;

    /** 搜索账号、姓名、邮箱、手机号，返回用户主键。 */
    @PostMapping("/users")
    public Result<List<Long>> users(@RequestParam("keyword") String keyword) throws IOException {
        return Result.success(service.search(keyword, false));
    }

    /** 搜索账号、姓名、技能名称，返回用户主键而不是处理人档案主键。 */
    @PostMapping("/handlers")
    public Result<List<Long>> handlers(@RequestParam("keyword") String keyword) throws IOException {
        return Result.success(service.search(keyword, true));
    }
}
