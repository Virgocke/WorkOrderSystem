package com.WorkOrder.handler.dto;

import com.WorkOrder.ticket.dto.MyTicketPageDto;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月16日 03:17
 * @description 处理工单分页查询参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandlerTicketPageDto extends MyTicketPageDto {
    @ApiModelProperty(value = "`deadline`（默认，截止时间升序）、`priority`、`createdAt`")
    private String sort;
}
