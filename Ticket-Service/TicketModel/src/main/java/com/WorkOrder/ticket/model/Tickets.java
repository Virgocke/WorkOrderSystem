package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 04:02
 * @description 工单实体类数据库表
 */
@Data
@TableName("tickets")
public class Tickets {
    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private Long categoryId;
    private String status;
    private Long creatorId;
    // 处理人ID
    private Long handlerId;
    private String createdAt;
    // 分配时间
    private String assignedAt;
    // 响应截止时间
    private String responseDeadline;
    // 解决截止时间
    private String resolutionDeadline;
    // 首次响应时间
    private String firstResponseAt;
    // 解决时间
    private String resolvedAt;
    // 关闭时间
    private String closedAt;
    // SLA状态
    private String slaStatus;
    // 升级级别
    private String escalatedLevel;
    // 来源
    private String source;
    private List<String> attachmentUrls;
    private String updatedAt;
}
