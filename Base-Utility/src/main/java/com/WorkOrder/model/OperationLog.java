package com.WorkOrder.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年09月15日 03:14
 * @description 操作日志
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OperationLog {

    private Long id;

    private Long ticketId;

    @ApiModelProperty(value = "操作:" +
            "CREATE/ASSIGN/TRANSFER/RESPOND/RESOLVE/CLOSE/CANCEL/REMIND/ESCALATE/INTERNAL_NOTE/USER_REPLY/HANDLER_REPLY" +
            "action=INTERNAL_NOT的时候对用户(role = 0)不可见")
    private String action;
    @ApiModelProperty(value = "操作员ID")
    private Long operatorId;
    @ApiModelProperty(value = "操作员名称")
    private String operatorName;
    @ApiModelProperty(value = "操作员角色:USER/HANDLER/ADMIN/SYSTEM")
    private String operatorRole;
    @ApiModelProperty(value = "操作内容")
    private String content;
    @ApiModelProperty(value = "附件 URL（图片回复/创建时携带）")
    private String[] attachments;

    private String createdAt;
}
