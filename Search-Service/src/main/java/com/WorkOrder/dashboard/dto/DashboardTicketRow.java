package com.WorkOrder.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 最新未终结工单及关联展示名称的数据库查询结果。
 */
@Data
public class DashboardTicketRow {

    /**
     * 工单 ID。
     */
    private Long id;

    /**
     * 工单编号。
     */
    private String ticketNo;

    /**
     * 工单标题。
     */
    private String title;

    /**
     * 工单详细描述。
     */
    private String description;

    /**
     * 工单分类 ID。
     */
    private Long categoryId;

    /**
     * 工单分类名称。
     */
    private String categoryName;

    /**
     * 工单优先级，取值为 1 至 4。
     */
    private int priority;

    /**
     * 工单状态。
     */
    private String status;

    /**
     * 创建人用户 ID。
     */
    private Long creatorId;

    /**
     * 创建人显示名称。
     */
    private String creatorName;

    /**
     * 当前处理人用户 ID，未分配时为空。
     */
    private Long handlerId;

    /**
     * 当前处理人显示名称，未分配时为空。
     */
    private String handlerName;

    /**
     * 工单创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 工单分配时间。
     */
    private LocalDateTime assignedAt;

    /**
     * 首次响应截止时间。
     */
    private LocalDateTime responseDeadline;

    /**
     * 工单解决截止时间。
     */
    private LocalDateTime resolutionDeadline;

    /**
     * 首次响应时间。
     */
    private LocalDateTime firstResponseAt;

    /**
     * 处理人提交解决方案的时间。
     */
    private LocalDateTime resolvedAt;

    /**
     * 工单关闭时间。
     */
    private LocalDateTime closedAt;

    /**
     * 工单 SLA 状态。
     */
    private String slaStatus;

    /**
     * 工单升级级别，0 表示未升级。
     */
    private int escalatedLevel;

    /**
     * 工单催办次数。
     */
    private int remindCount;

    /**
     * 工单来源，例如 WEB、APP 或 API。
     */
    private String source;

    /**
     * 数据库返回的附件 URL JSON 数组文本。
     */
    private String attachmentUrls;

    /**
     * 工单最后更新时间。
     */
    private LocalDateTime updatedAt;
}
