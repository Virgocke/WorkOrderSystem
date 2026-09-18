package com.WorkOrder.notification.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知记录实体。 */
@Data
@TableName("notification_records")
public class Notifications {

    private Long id;
    private Long ticketId;
    private Long receiverId;
    private String channel;
    private String content;
    /** 数据库存储的投递状态，PENDING/SENT/READ/FAILED。 */
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 列表查询冗余的工单编号，系统通知为空。 */
    @TableField(exist = false)
    private String ticketNo;
}
