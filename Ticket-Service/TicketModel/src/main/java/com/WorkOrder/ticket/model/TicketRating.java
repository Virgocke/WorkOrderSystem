package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月15日 20:51
 * @description 工单评价实体类
 */
@Data
@TableName("ticket_ratings")
public class TicketRating {
    private Long id;
    private Long ticketId;
    private Long userId;
    private int rating;
    private String comment;
    private String createdAt;
    private String updatedAt;
}
