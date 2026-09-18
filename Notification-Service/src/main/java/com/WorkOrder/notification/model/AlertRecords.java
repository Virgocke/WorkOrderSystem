package com.WorkOrder.notification.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月19日 00:19
 * @description 告警记录实体类
 */
@Data
@TableName("alert_records")
public class AlertRecords {
    private Long id;
    @ApiModelProperty(value = "工单ID")
    private Long ticketId;
    @ApiModelProperty(value = "告警类型", example = "RESPONSE_TIMEOUT/RESOLUTION/ESCALATION")
    private String alertType;
    @ApiModelProperty(value = "告警级别", example = "1/2/3")
    private int level;
    @ApiModelProperty(value = "告警消息")
    private String message;
    @ApiModelProperty(value = "目标用户ID")
    private Long targetUserId;
    @ApiModelProperty(value = "通知渠道", example = "EMAIL/SMS/INTERNAL")
    private String notificationChannel;
    @ApiModelProperty(value = "告警状态", example = "PENDING/SENT/FAILED")
    private String status;
    @ApiModelProperty(value = "发送时间")
    private LocalDateTime sentAt;
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;
    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedAt;
}
