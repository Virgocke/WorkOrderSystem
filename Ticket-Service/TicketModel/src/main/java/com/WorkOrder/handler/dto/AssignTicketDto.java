package com.WorkOrder.handler.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * 管理员手动分配工单DTO。
 */
@Data
public class AssignTicketDto {

    @NotNull(message = "处理人ID不能为空")
    @Positive(message = "处理人ID必须为正数")
    private Long handlerId;

    /** 分配原因，未填写时使用默认说明。 */
    @Size(max = 500, message = "分配原因不能超过500个字符")
    private String reason;
}
