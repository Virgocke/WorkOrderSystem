package com.WorkOrder.ticket.feignclient;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.assignment.AssignCandidate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 获取 Assign-Engine 的同一套工单和处理人评分。
 */
@FeignClient(name = "Assign-Engine", path = "/assign-engine", configuration = TicketFeignConfiguration.class)
public interface AssignmentScoreFeignClient {
    /**
     * 处理 score 对应的分配评分Feign客户端操作。
     *
     * @param ticketId 待分配工单 ID
     * @param handlerId 目标处理人用户 ID
     * @param additionalLoad 同一批事务内尚未提交的新增在办工单数
     * @return 综合分与各项分数
     */
    @GetMapping("/score")
    Result<AssignCandidate> score(@RequestParam("ticketId") Long ticketId,
                                  @RequestParam("handlerId") Long handlerId,
                                  @RequestParam("additionalLoad") int additionalLoad);
}
