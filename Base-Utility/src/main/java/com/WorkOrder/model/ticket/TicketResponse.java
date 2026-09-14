package com.WorkOrder.model.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 03:51
 * @description 工单响应对象，供工单列表和详情接口使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    /** 工单 ID */
    private Long id;

    /** 工单编号，例如 WO202609081234 */
    private String ticketNo;

    /** 标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 分类 ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 优先级：1紧急、2高、3中、4低 */
    private int priority;

    /** 工单状态 */
    private String status;

    /** 创建人用户 ID */
    private Long creatorId;

    /** 创建人姓名 */
    private String creatorName;

    /** 处理人用户 ID，未分配时为 null */
    private Long handlerId;

    /** 处理人姓名，未分配时为 null */
    private String handlerName;

    /** 创建时间 */
    private String createdAt;

    /** 分配时间 */
    private String assignedAt;

    /** 响应截止时间 */
    private String responseDeadline;

    /** 解决截止时间 */
    private String resolutionDeadline;

    /** 首次响应时间 */
    private String firstResponseAt;

    /** 处理人提交解决时间 */
    private String resolvedAt;

    /** 关闭时间 */
    private String closedAt;

    /** SLA 状态 */
    private String slaStatus;

    /** 升级级别，0 表示未升级 */
    private int escalatedLevel;

    /** 来源：WEB、APP、API */
    private String source;

    /** 图片附件 URL */
    private List<String> attachmentUrls;

    /** 更新时间 */
    private String updatedAt;
}
