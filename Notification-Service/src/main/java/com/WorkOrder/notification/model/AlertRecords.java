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
    /**
     * 告警记录主键。
     */
    private Long id;
    /**
     * 关联工单 ID。
     */
    @ApiModelProperty(value = "工单ID")
    private Long ticketId;
    /**
     * 告警发生时的工单编号；旧告警可由迁移脚本补录当前编号。
     */
    @ApiModelProperty(value = "工单编号快照")
    private String ticketNoSnapshot;
    /**
     * 告警发生时的工单标题；旧告警可由迁移脚本补录当前标题，无法补录时为空。
     */
    @ApiModelProperty(value = "工单标题快照")
    private String ticketTitleSnapshot;
    /**
     * 告警类型：响应超时、解决超时或升级。
     */
    @ApiModelProperty(value = "告警类型", example = "RESPONSE_TIMEOUT/RESOLUTION_TIMEOUT/ESCALATION")
    private String alertType;
    /**
     * 告警级别：1 为提醒，2 为警告，3 为严重。
     */
    @ApiModelProperty(value = "告警级别", example = "1/2/3")
    private int level;
    /**
     * 告警消息，最多保存 500 个字符。
     */
    @ApiModelProperty(value = "告警消息")
    private String message;
    /**
     * 告警接收人 ID，处理人只能查看和处理发给自己的告警。
     */
    @ApiModelProperty(value = "目标用户ID")
    private Long targetUserId;
    /**
     * 告警使用的通知渠道。
     */
    @ApiModelProperty(value = "通知渠道", example = "EMAIL/INTERNAL")
    private String notificationChannel;
    /**
     * 发送或处理状态：待发送、已发送、发送失败、已处理。
     */
    @ApiModelProperty(value = "告警状态", example = "PENDING/SENT/FAILED/HANDLED")
    private String status;
    /**
     * 发送时间，尚未发送时为空。
     */
    @ApiModelProperty(value = "发送时间")
    private LocalDateTime sentAt;
    /**
     * 告警创建时间。
     */
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;
    /**
     * 最后更新时间；摘要补录和重复处理不改变此值。
     */
    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedAt;
}
