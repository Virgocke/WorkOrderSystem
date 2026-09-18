package com.WorkOrder.notification.dto;

import com.WorkOrder.model.page.PageParams;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月19日 00:10
 * @description
 */
@Data
public class AlertDto extends PageParams {

    @ApiModelProperty(value = "告警类型", example = "RESPONSE_TIMEOUT/RESOLUTION/ESCALATION")
    private String type;
    @ApiModelProperty(value = "告警状态", example = "PENDING/SENT/FAILED/HANDLED")
    private String status;
}
