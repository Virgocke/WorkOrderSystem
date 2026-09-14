package com.WorkOrder.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月15日 03:14
 * @description 状态历史记录类，记录工单状态变化历史
 */
@Data
public class StatusHistory {
    private Long id;
    private Long ticketId;
    @ApiModelProperty(value = "源状态")
    private String fromStatus = "";
    @ApiModelProperty(value = "目标状态")
    private String toStatus;
    @ApiModelProperty(value = "事件：" +
            "CREATE/ASSIGN/TRANSFER/RESPOND/SUBMIT_RESOLUTION/CONFIRM_RESOLUTION/FORCE_CLOSE/CANCEL/ESCALATE")
    private String event;
    @ApiModelProperty(value = "操作员ID")
    private Long operatorId;
    @ApiModelProperty(value = "操作员名称")
    private String operatorName;
    @ApiModelProperty(value = "备注")
    private String remark;
    private String createdAt;
}
