package com.WorkOrder.notification.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "userAndHandler-service", path = "/users", configuration = NotificationFeignConfiguration.class)
public interface UserFeignClient {

    @GetMapping("/{id}")
    Result<UserProfile> getById(@PathVariable Long id);
}