package com.WorkOrder.ticket.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 03:33
 * @description 创建工单DTO
 */
@Data
public class CreateTicketDto {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String description;
    private Long categoryId;
    @Min(0)
    @Max(4)
    private int priority;
    private List<String> attachmentUrls;
}
