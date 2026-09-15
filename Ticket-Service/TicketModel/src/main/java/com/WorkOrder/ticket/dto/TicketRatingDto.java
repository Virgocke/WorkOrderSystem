package com.WorkOrder.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @author Virgor
 * @date 2026年09月16日 01:20
 * @description 工单评分数据传输对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketRatingDto {
    @Min(1)
    @Max(5)
    private int rating;
    private String comment;
}
