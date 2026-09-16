package com.WorkOrder.admin.dto;

import com.WorkOrder.ticket.dto.MyTicketPageDto;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月17日 01:46
 * @description 管理员工单列表查询参数
 */
@Data
public class AdminTicketListDto extends MyTicketPageDto {
    @ApiModelProperty(value = "优先级过滤")
    private int priority;
    @ApiModelProperty(value = "类别过滤")
    private Long categoryId;
    @ApiModelProperty(value = "处理人过滤")
    private Long handlerId;
    @ApiModelProperty(value = "创建人过滤")
    private Long creatorId;
    @ApiModelProperty(value = "sla状态过滤")
    private String slaStatus;
    @ApiModelProperty(value = "开始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime start;
    @ApiModelProperty(value = "结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime end;
}
