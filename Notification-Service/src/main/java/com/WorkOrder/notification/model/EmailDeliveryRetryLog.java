package com.WorkOrder.notification.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 已提交的人工重发操作，同时作为 requestId 幂等结果。
 */
@Data
@TableName("notification_email_retry_logs")
public class EmailDeliveryRetryLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deliveryId;
    private Long notificationId;
    private String requestId;
    private Integer fromRound;
    private Integer toRound;
    private Integer previousAttempts;
    private Long previousTotalAttempts;
    private String previousError;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
