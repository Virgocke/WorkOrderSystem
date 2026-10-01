package com.WorkOrder.dashboard.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/** 管理员审计查询响应；姓名取当前用户信息。 */
@Data
public class TicketAuditItem {
    /** OPERATION:<id> 或 STATUS:<id>，跨表稳定唯一。 */
    private String id;
    /** 日志来源，OPERATION 或 STATUS。 */
    private String source;
    /** 来源表中的原始主键。 */
    private Long sourceId;
    /** 关联工单主键。 */
    private Long ticketId;
    /** 工单编号筛选或响应值。 */
    private String ticketNo;
    /** 操作动作，状态历史带 STATUS_ 前缀。 */
    private String action;
    /** 操作人用户 ID；系统动作可为空。 */
    private Long operatorId;
    /** 操作人当前姓名，账号缺失时显示用户 ID。 */
    private String operatorName;
    /** 日志记录的角色；状态历史缺失角色时取当前用户角色。 */
    private String operatorRole;
    /** 操作内容或状态变化说明。 */
    private String content;
    /** 操作发生时间，按项目本地时间输出。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
