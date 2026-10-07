package com.WorkOrder.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员批量分配工单DTO。
 */
@Data
public class AssignTicketListDto {

    @NotEmpty(message = "工单ID列表不能为空")
    private List<@NotNull(message = "工单ID不能为空") @Positive(message = "工单ID必须为正数") Long> ids;

    @NotNull(message = "处理人ID不能为空")
    @Positive(message = "处理人ID必须为正数")
    private Long handlerId;
}
