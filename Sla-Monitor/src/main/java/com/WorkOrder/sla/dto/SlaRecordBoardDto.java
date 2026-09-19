package com.WorkOrder.sla.dto;

import com.WorkOrder.model.page.PageParams;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月19日 04:35
 * @description SlaRecordBoardDto 工单SLA记录看板DTO
 */
@Data
public class SlaRecordBoardDto extends PageParams {
    @ApiModelProperty(value = "SLA状态",
            example = "NORMAL/NEAR_TIMEOUT/TIMEOUT/ESCALATED")
    private String slaStatus;
    @ApiModelProperty(value = "工单状态",
            example = "PENDING_ASSIGN/PENDING_RESPONSE/PROCESSING/RESOLVED/CLOSED/CANCELLED")
    private String status;
}
