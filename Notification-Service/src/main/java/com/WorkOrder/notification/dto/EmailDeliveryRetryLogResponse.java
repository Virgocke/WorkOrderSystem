package com.WorkOrder.notification.dto;

import com.WorkOrder.notification.model.EmailDeliveryRetryLog;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 专用邮件重发审计，不复制邮件正文或 SMTP 异常堆栈。
 */
@Data
public class EmailDeliveryRetryLogResponse {
    private String id;
    private String deliveryId;
    private String notificationId;
    private String requestId;
    private Integer fromRound;
    private Integer toRound;
    private Integer previousAttempts;
    private Long previousTotalAttempts;
    private String previousError;
    private String operatorId;
    private String reason;
    private String createdAt;

    /**
     * 对外 ID 使用字符串，避免 JavaScript 数值精度丢失。
     *
     * @param log 已经提交成功的人工重发审计记录
     * @return 公开审计响应，主键和操作人 ID 使用字符串，不包含邮件正文
     */
    public static EmailDeliveryRetryLogResponse from(EmailDeliveryRetryLog log) {
        EmailDeliveryRetryLogResponse response = new EmailDeliveryRetryLogResponse();
        response.setId(String.valueOf(log.getId()));
        response.setDeliveryId(String.valueOf(log.getDeliveryId()));
        response.setNotificationId(String.valueOf(log.getNotificationId()));
        response.setRequestId(log.getRequestId());
        response.setFromRound(log.getFromRound());
        response.setToRound(log.getToRound());
        response.setPreviousAttempts(log.getPreviousAttempts());
        response.setPreviousTotalAttempts(log.getPreviousTotalAttempts());
        response.setPreviousError(log.getPreviousError());
        response.setOperatorId(String.valueOf(log.getOperatorId()));
        response.setReason(log.getReason());
        response.setCreatedAt(EmailDeliveryAdminResponse.formatTime(log.getCreatedAt()));
        return response;
    }
}
