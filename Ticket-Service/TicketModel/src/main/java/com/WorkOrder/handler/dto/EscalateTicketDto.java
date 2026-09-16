package com.WorkOrder.handler.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author Virgor
 * @date 2026年09月17日 00:44
 * @description 升级工单DTO，用于升级工单时提供原因
 */
@Data
public class EscalateTicketDto {
    @NotBlank(message = "原因不能为空")
    private String reason;
}
