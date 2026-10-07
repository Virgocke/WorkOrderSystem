package com.WorkOrder.notification.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 用户Feign客户端契约。
 */
@FeignClient(name = "userAndHandler-service", path = "/users", configuration = NotificationFeignConfiguration.class)
public interface UserFeignClient {

    /**
     * 从用户服务读取指定账号的当前公开资料。
     *
     * @param id 需要查询当前资料及账号状态的用户主键
     * @return 用户服务统一响应，包含用户资料或业务错误
     */
    @GetMapping("/{id}")
    Result<UserProfile> getById(@PathVariable("id") Long id);
}
