package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月14日 04:02
 * @description 工单实体类数据库表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    /** 提交解决时负责该工单的处理人用户 ID，转派后记入接手人。 */
    private Long resolvedByHandlerId;

    private LocalDateTime createdAt;
    // 分配时间
    private LocalDateTime assignedAt;
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
    // 催办次数，与升级级别独立，每张工单最多 3 次
    private int remindCount;
    // 来源
    private String source;

    /** 工单已提交状态的源版本，由业务 UPDATE 原子递增。 */
    private Long sourceVersion;

    private LocalDateTime updatedAt;
}
