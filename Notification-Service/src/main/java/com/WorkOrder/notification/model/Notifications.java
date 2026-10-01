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
    /** 生成该通知的领域事件 ID，与接收人和渠道共同构成业务幂等键。 */
    @TableField("source_event_id")
    private String sourceEventId;
    private Long ticketId;
    /** 技能审核通知关联的申请 ID，工单通知为空。 */
    private Long skillApplicationId;
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
