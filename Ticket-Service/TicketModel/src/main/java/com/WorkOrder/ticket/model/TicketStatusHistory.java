package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月15日 03:21
 * @description 工单状态历史记录
 */
@TableName("ticket_status_history")
@Data
public class TicketStatusHistory {
    @ApiModelProperty(value = "ID")
    private Long id;
    @ApiModelProperty(value = "工单ID")
    private Long ticketId;
    @ApiModelProperty(value = "从状态")
    private String fromStatus;
    @ApiModelProperty(value = "到状态")
    private String toStatus;
    @ApiModelProperty(value = "事件")
    private String event;
    @ApiModelProperty(value = "操作员ID")
    private Long operatorId;
    @ApiModelProperty(value = "备注")
    private String remark;
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;
    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedAt;
}
