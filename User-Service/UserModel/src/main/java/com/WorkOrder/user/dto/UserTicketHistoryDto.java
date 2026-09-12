package com.WorkOrder.user.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 00:29
 * @description 用户工单历史统计DTO
 */
@Data
public class UserTicketHistoryDto {
    @ApiModelProperty(value = "总工单数")
    private int total;
    @ApiModelProperty(value = "处理中的工单数")
    private int processing;
    @ApiModelProperty(value = "已解决的工单数")
    private int resolved;
    @ApiModelProperty(value = "已关闭的工单数")
    private int closed;
    @ApiModelProperty(value = "已取消的工单数")
    private int cancelled;
    @ApiModelProperty(value = "平均首次响应小时数")
    private int avgFirstResponseHours;
    @ApiModelProperty(value = "最近30天的工单数")
    private int last30d;

    @ApiModelProperty(value = "按月统计的工单数")
    private List<MonthlyCountDto> byMonth;
}
