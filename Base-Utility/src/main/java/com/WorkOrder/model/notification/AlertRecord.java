package com.WorkOrder.model.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description SLA 告警记录响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecord {

    /**
     * 告警记录 ID。
     */
    private Long id;

    /**
     * 关联工单 ID。
     */
    private Long ticketId;

    /**
     * 告警发生时的工单编号快照；旧告警可由迁移补录，缺失时为空。
     */
    private String ticketNo;

    /**
     * 告警发生时的工单标题快照；旧告警补录的是迁移时标题，缺失时为空。
     */
    private String ticketTitle;

    /**
     * 告警类型：RESPONSE_TIMEOUT、RESOLUTION_TIMEOUT、ESCALATION。
     */
    private String alertType;

    /**
     * 告警级别：1-提醒，2-警告，3-严重。
     */
    private int level;

    /**
     * 告警内容。
     */
    private String message;

    /**
     * 通知对象用户 ID。
     */
    private Long targetUserId;

    /**
     * 通知对象姓名，列表查询时冗余返回。
     */
    private String targetName;

    /**
     * 通知渠道，例如 INTERNAL、EMAIL、SMS。
     */
    private String channel;

    /**
     * 告警状态：PENDING、SENT、FAILED、HANDLED。
     */
    private String status;

    /**
     * 发送时间；尚未发送时为 null。
     */
    private String sentAt;

    /**
     * 创建时间，格式为 yyyy-MM-dd HH:mm:ss。
     */
    private String createdAt;
}
