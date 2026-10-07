package com.WorkOrder.handler.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 处理人提交工单解决方案的请求。
 */
@Data
public class TicketSolutionDto {

    @NotBlank(message = "解决方案不能为空")
    private String solution;
}