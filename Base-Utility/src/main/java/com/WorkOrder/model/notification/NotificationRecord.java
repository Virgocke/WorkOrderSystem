package com.WorkOrder.model.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知记录响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {

    /** 通知 ID。 */
    private Long id;

    /** 关联工单 ID；系统通知可为 null。 */
    private Long ticketId;

    /** 工单编号，列表查询时冗余返回；系统通知可为 null。 */
    private String ticketNo;

    /** 接收人用户 ID。 */
    private Long receiverId;

    /** 通知渠道，例如 INTERNAL、EMAIL。 */
    private String channel;

    /** 通知内容。 */
    private String content;

    /** 前端阅读状态：UNREAD、READ。 */
    private String status;

    /** 创建时间，格式为 yyyy-MM-dd HH:mm:ss。 */
    private String createdAt;
}
