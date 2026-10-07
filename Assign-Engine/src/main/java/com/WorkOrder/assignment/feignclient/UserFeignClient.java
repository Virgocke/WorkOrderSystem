package com.WorkOrder.assignment.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 调用用户服务，读取用户资料。
 */
@FeignClient(name = "userAndHandler-service", path = "/users", configuration = AssignmentFeignConfiguration.class)
public interface UserFeignClient {

    /**
     * 按用户 ID 读取用户资料。
     *
     * @param id 用户 ID
     * @return 用户资料的统一响应
     */
    @GetMapping("/{id}")
    Result<UserProfile> getById(@PathVariable("id") Long id);
}
