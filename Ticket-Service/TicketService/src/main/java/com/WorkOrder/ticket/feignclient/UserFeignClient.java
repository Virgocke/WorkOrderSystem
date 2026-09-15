package com.WorkOrder.ticket.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.user.UserProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/users")
public interface UserFeignClient {

    @GetMapping("/{id}")
    Result<UserProfile> getById(@PathVariable Long id);
}