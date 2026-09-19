package com.WorkOrder.sla.dto;

import com.WorkOrder.model.page.PageParams;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Pattern;

/**
 * @author Virgor
 * @date 2026年09月19日 04:35
 * @description SlaRecordBoardDto 工单SLA记录看板DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SlaRecordBoardDto extends PageParams {
    @ApiModelProperty(value = "SLA状态",
            example = "NORMAL/NEAR_TIMEOUT/TIMEOUT/ESCALATED")
    @Pattern(regexp = "^(NORMAL|NEAR_TIMEOUT|TIMEOUT|ESCALATED)?$", message = "SLA状态不合法")
    private String slaStatus;
    @ApiModelProperty(value = "工单状态",
            example = "PENDING_RESPONSE/PROCESSING/RESOLVED")
    @Pattern(regexp = "^(PENDING_RESPONSE|PROCESSING|RESOLVED)?$", message = "工单状态不合法")
    private String status;
}
