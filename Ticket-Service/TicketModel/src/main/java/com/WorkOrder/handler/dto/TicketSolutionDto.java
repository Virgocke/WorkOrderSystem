package com.WorkOrder.handler.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class TicketSolutionDto {

    @NotBlank(message = "解决方案不能为空")
    private String solution;
}