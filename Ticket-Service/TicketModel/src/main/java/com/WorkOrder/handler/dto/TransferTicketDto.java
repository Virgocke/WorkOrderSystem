package com.WorkOrder.handler.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
    private String reason;
}
