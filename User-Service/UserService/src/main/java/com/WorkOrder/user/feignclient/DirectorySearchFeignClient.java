package com.WorkOrder.user.feignclient;

import com.WorkOrder.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 用户和处理人共用搜索服务，返回完整候选 ID，分页及当前业务条件仍由数据库执行。
 */
@FeignClient(name = "Search", contextId = "directorySearch", path = "/internal/search/directory",
        configuration = DirectorySearchFeignConfiguration.class)
public interface DirectorySearchFeignClient {
    /**
     * 按账号、姓名、邮箱和手机号搜索用户。
     *
     * @param keyword 查询关键词
     * @return 统一响应，包含长整型数值列表
     */
    @PostMapping("/users")
    Result<List<Long>> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 按账号、姓名和技能名称搜索处理人，不匹配邮箱及手机号。
     *
     * @param keyword 查询关键词
     * @return 统一响应，包含长整型数值列表
     */
    @PostMapping("/handlers")
    Result<List<Long>> searchHandlers(@RequestParam("keyword") String keyword);
}
