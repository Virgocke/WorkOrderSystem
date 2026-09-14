package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
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
    private int priority;
    private String status;
    private Long creatorId;
    // 处理人ID
    private Long handlerId;

    private String createdAt;
    // 分配时间
    private String assignedAt;
    // 响应截止时间
    private LocalDateTime responseDeadline;
    // 解决截止时间
    private LocalDateTime resolutionDeadline;
    // 首次响应时间
    private LocalDateTime firstResponseAt;
    // 解决时间
    private LocalDateTime resolvedAt;
    // 关闭时间
    private LocalDateTime closedAt;
    // SLA状态
    private String slaStatus;
    // 升级级别
    private int escalatedLevel;
    // 来源
    private String source;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;
    private String updatedAt;
}
