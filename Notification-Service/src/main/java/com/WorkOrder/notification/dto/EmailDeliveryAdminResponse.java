package com.WorkOrder.notification.dto;

import com.WorkOrder.notification.model.EmailDelivery;
import com.WorkOrder.notification.service.EmailRecipientValidator;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 邮件管理公开字段；列表不公开原地址，始终不公开正文、令牌和租约。
 */
@Data
public class EmailDeliveryAdminResponse {
    private String id;
    private String notificationId;
    private String recipientMasked;
    /**
     * 仅管理员详情返回原收件地址。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String recipient;
    private String subject;
    private String status;
    private Integer retryRound;
    private Integer attempts;
    private Long totalAttempts;
    private String lastError;
    private String createdAt;
    private String updatedAt;
    private String sentAt;
    private boolean canRetry;
    private String cannotRetryReason;
    private boolean workerEnabled;

    /**
     * 从明确的字段清单生成响应，不能直接序列化邮件任务实体。
     *
     * @param task 查询得到的邮件任务公开字段快照
     * @param detail 为 true 时额外返回原收件地址；列表仅返回脱敏地址
     * @param workerEnabled 当前邮件工作者是否启用，与渠道开关独立
     * @return 不含正文、令牌及租约的管理响应，包含当前重发资格及阻止原因
     */
    public static EmailDeliveryAdminResponse from(EmailDelivery task, boolean detail, boolean workerEnabled) {
        EmailDeliveryAdminResponse response = new EmailDeliveryAdminResponse();
        response.setId(String.valueOf(task.getId()));
        response.setNotificationId(String.valueOf(task.getNotificationId()));
        response.setRecipientMasked(maskAddress(task.getRecipient()));
        response.setRecipient(detail ? task.getRecipient() : null);
        response.setSubject(task.getSubject());
        response.setStatus(task.getStatus());
        response.setRetryRound(task.getRetryRound());
        response.setAttempts(task.getAttempts());
        response.setTotalAttempts(task.getTotalAttempts());
        response.setLastError(task.getLastError());
        response.setCreatedAt(formatTime(task.getCreatedAt()));
        response.setUpdatedAt(formatTime(task.getUpdatedAt()));
        response.setSentAt(formatTime(task.getSentAt()));
        response.setWorkerEnabled(workerEnabled);
        String blocked = cannotRetryReason(task);
        response.setCanRetry(blocked == null);
        response.setCannotRetryReason(blocked);
        return response;
    }

    /**
     * 与重试服务使用相同的任务资格，前端提示不代替服务端条件校验。
     *
     * @param task 需要检查失败状态、原地址及轮次的当前邮件任务快照
     * @return 不能开启新轮次的原因；满足重发条件时为 null
     */
    private static String cannotRetryReason(EmailDelivery task) {
        if (!"FAILED".equals(task.getStatus())) {
            return "任务不是失败状态，请刷新后查看";
        }
        if (!EmailRecipientValidator.isValid(task.getRecipient())) {
            return "原收件邮箱缺失或不合法";
        }
        if (task.getRetryRound() == null || task.getRetryRound() == Integer.MAX_VALUE) {
            return "重发轮次不可用";
        }
        return null;
    }

    /**
     * 列表仅保留如 a***@b.com，非法地址不泄露原始文本。
     *
     * @param address 原收件邮箱快照，可为空
     * @return 保留本地部分首字符和完整域名的脱敏地址；地址缺失或不合法时为 null
     */
    private static String maskAddress(String address) {
        if (!EmailRecipientValidator.isValid(address)) {
            return null;
        }
        int separator = address.lastIndexOf('@');
        return address.substring(0, 1) + "***" + address.substring(separator);
    }

    /**
     * 与既有通知接口保持相同的本地时间格式。
     *
     * @param time 待展示的本地任务或审计时间，可为空
     * @return yyyy-MM-dd HH:mm:ss 格式的时间文本；输入为空时为 null
     */
    public static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
