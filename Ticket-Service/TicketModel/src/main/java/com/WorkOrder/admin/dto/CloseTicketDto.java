package com.WorkOrder.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author Virgor
 * @date 2026年09月17日 04:22
 * @description 关闭工单参数DTO
 */
@Data
public class CloseTicketDto {
    @NotBlank
    private String reason;
}
