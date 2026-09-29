package com.WorkOrder.handler.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月16日 23:13
 * @description 转交工单DTO
 */
@Data
public class TransferTicketDto {
    @NotNull(message = "转交人ID不能为空")
    private Long toHandlerId;
    @NotBlank(message = "转交原因不能为空")
    @Size(max = 300, message = "转交原因不能超过300字")
    private String reason;
}
