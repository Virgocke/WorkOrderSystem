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

@FeignClient(name = "userAndHandler-service", path = "/users", configuration = TicketFeignConfiguration.class)
public interface UserFeignClient {

    @GetMapping("/{id}")
    Result<UserProfile> getById(@PathVariable("id") Long id);

    @PostMapping("/batch")
    Result<List<UserProfile>> getByIds(@RequestBody Collection<Long> ids);
}
