package com.WorkOrder.sla.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月19日 04:23
 * @description SlaRecord 工单SLA记录类
 */
@Data
@TableName("sla_records")
public class SlaRecord {
    private Long id;
    @ApiModelProperty(value = "工单ID")
    private Long ticketId;
    @ApiModelProperty(value = "响应截止时间")
    private LocalDateTime responseDeadline;
    @ApiModelProperty(value = "解决截止时间")
    private LocalDateTime resolutionDeadline;
    @ApiModelProperty(value = "首次响应时间")
    private LocalDateTime firstResponseTime;
    @ApiModelProperty(value = "解决时间")
    private LocalDateTime resolvedAt;
    @ApiModelProperty(value = "响应时长（分钟）")
    private int responseDurationMin;
    @ApiModelProperty(value = "解决时长（分钟）")
    private int resolutionDurationMin;
    @ApiModelProperty(value = "是否响应超时")
    private int isResponseTimeout;
    @ApiModelProperty(value = "是否解决超时")
    private int isResolutionTimeout;
    @ApiModelProperty(value = "当前 escalation 级别")
    private int currentEscalationLevel;
    /**
     * 工单首次进入的终态，CLOSED 或 CANCELLED。
     */
    private String terminalStatus;
    /**
     * 工单首次进入终态的时间。
     */
    private LocalDateTime terminalAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
