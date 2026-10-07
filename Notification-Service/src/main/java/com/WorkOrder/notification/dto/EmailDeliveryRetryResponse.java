package com.WorkOrder.notification.dto;

import lombok.Data;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description HTTP 成功仅确认排队事务；当前状态可能已经由工作者推进。
 */
@Data
public class EmailDeliveryRetryResponse {
    private String deliveryId;
    private Integer acceptedRetryRound;
    private String currentStatus;
    private boolean workerEnabled;

    /**
     * 使用已接受轮次和最新状态生成响应，包括幂等重放的响应。
     *
     * @param deliveryId 本次操作关联的原邮件任务主键
     * @param round 此次请求已被接受的目标轮次，幂等重放沿用历史值
     * @param status 读取时邮件任务的当前状态，不保证仍为 PENDING
     * @param workerEnabled 当前邮件工作者是否启用，为 false 时排队等待恢复
     * @return 人工重发受理信息，仅表示事务提交成功，不表示 SMTP 已发送
     */
    public static EmailDeliveryRetryResponse accepted(Long deliveryId, Integer round,
                                                      String status, boolean workerEnabled) {
        EmailDeliveryRetryResponse response = new EmailDeliveryRetryResponse();
        response.setDeliveryId(String.valueOf(deliveryId));
        response.setAcceptedRetryRound(round);
        response.setCurrentStatus(status);
        response.setWorkerEnabled(workerEnabled);
        return response;
    }
}
