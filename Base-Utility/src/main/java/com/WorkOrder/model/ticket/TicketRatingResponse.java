package com.WorkOrder.model.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * @author Virgor
 * @date 2026年09月15日 20:51
 * @description 工单评价响应实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketRatingResponse {

    /**
     * 评价 ID
     */
    private Long id;

    /**
     * 工单 ID
     */
    private Long ticketId;

    /**
     * 工单编号
     */
    private String ticketNo;

    /**
     * 评价用户 ID
     */
    private Long userId;

    /**
     * 评价用户姓名
     */
    private String userName;

    /**
     * 工单处理人姓名
     */
    private String handlerName;

    /**
     * 评分：1～5
     */
    @Min(1)
    @Max(5)
    private Integer rating;

    /**
     * 评价内容
     */
    @Size(max = 500, message = "评价内容不能超过500个字符")
    private String comment;

    /**
     * 评价时间
     */
    private String createdAt;
}