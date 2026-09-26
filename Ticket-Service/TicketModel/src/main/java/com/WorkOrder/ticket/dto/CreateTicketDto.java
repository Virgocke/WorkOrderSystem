package com.WorkOrder.ticket.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 03:33
 * @description 创建工单DTO
 */
@Data
public class CreateTicketDto {
    @NotBlank(message = "标题不能为空")
    @Size(min = 3, max = 100, message = "标题长度不能超过 100")
    private String title;
    private String description;
    private Long categoryId;
    @Min(0)
    @Max(4)
    private int priority;
    @Size(max = 9, message = "工单最多上传 9 个附件")
    private List<Long> attachmentIds;
}
