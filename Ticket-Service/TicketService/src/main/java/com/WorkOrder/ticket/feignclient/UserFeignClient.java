package com.WorkOrder.ticket.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 调用用户服务，读取用户资料。
 */
@FeignClient(name = "userAndHandler-service", path = "/users", configuration = TicketFeignConfiguration.class)
public interface UserFeignClient {

    /**
     * 按用户 ID 读取用户资料。
     *
     * @param id 用户 ID
     * @return 用户资料的统一响应
     */
    @GetMapping("/{id}")
    Result<UserProfile> getById(@PathVariable("id") Long id);

    /**
     * 批量读取用户资料。
     *
     * @param ids 用户 ID 集合
     * @return 查询到的用户资料列表响应
     */
    @PostMapping("/batch")
    Result<List<UserProfile>> getByIds(@RequestBody Collection<Long> ids);
}
