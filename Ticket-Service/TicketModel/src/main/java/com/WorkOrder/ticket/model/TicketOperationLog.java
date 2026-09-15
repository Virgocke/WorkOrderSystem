package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月15日 20:01
 * @description 工单操作日志数据库表结构
 */
@Data
@TableName("ticket_operation_log")
public class TicketOperationLog {
    private Long id;
    private Long ticketId;
    @ApiModelProperty(value = "操作:" +
            "CREATE/ASSIGN/TRANSFER/RESPOND/RESOLVE/CLOSE/CANCEL/REMIND/ESCALATE/INTERNAL_NOTE/USER_REPLY/HANDLER_REPLY" +
            "action=INTERNAL_NOT的时候对用户(role = 0)不可见")
    private String action;
    private Long operatorId;
    @ApiModelProperty(value = "操作员角色:USER/HANDLER/ADMIN/SYSTEM")
    private String operatorRole;
    private String content;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
