package com.WorkOrder.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 一次源查询取得的完整工单搜索字段和版本，不联接展示名称或其他业务表。 */
@Getter
@Setter
public class TicketIndexSource {
    /** 工单数值主键。 */
    private Long ticketId;
    /** 工单业务编号。 */
    private String ticketNo;
    /** 工单标题。 */
    private String title;
    /** 可空的详细描述。 */
    private String description;
    /** 分类主键。 */
    private Long categoryId;
    /** 源业务定义的优先级。 */
    private Integer priority;
    /** 当前工单状态。 */
    private String status;
    /** 创建人主键。 */
    private Long creatorId;
    /** 尚未分配时为空的处理人主键。 */
    private Long handlerId;
    /** 当前 SLA 状态。 */
    private String slaStatus;
    /** MySQL DATETIME 创建时间。 */
    private LocalDateTime createdAt;
    /** MySQL DATETIME 首次响应截止时间。 */
    private LocalDateTime responseDeadline;
    /** MySQL DATETIME 更新时间。 */
    private LocalDateTime updatedAt;
    /** 与本行所有字段同时读取的源版本。 */
    private Long sourceVersion;
}
