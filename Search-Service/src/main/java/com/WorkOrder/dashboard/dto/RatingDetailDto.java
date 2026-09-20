package com.WorkOrder.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理端评价明细。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDetailDto {

    /** 评价 ID。 */
    private Long id;

    /** 工单 ID。 */
    private Long ticketId;

    /** 工单编号。 */
    private String ticketNo;

    /** 工单标题。 */
    private String ticketTitle;

    /** 评价用户 ID。 */
    private Long userId;

    /** 评价用户显示名称。 */
    private String userName;

    /** 工单处理人 ID。 */
    private Long handlerId;

    /** 工单处理人显示名称。 */
    private String handlerName;

    /** 评分，取值范围为 1 至 5。 */
    private Integer rating;

    /** 评价内容。 */
    private String comment;

    /** 评价时间，格式为 yyyy-MM-dd HH:mm:ss。 */
    private String createdAt;
}
