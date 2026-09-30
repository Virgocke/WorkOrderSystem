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
    /** 评价归属的解决处理人用户 ID，不随之后的工单变更而变化。 */
    private Long handlerId;
    private int rating;
    private String comment;
    private String createdAt;
    private String updatedAt;
}
