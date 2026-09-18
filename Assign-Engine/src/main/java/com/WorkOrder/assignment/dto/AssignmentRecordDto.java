package com.WorkOrder.assignment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分配记录响应 DTO。
 */
@Data
@ApiModel(description = "分配记录响应")
public class AssignmentRecordDto {

    @ApiModelProperty(value = "分配记录ID", example = "50", dataType = "java.lang.String")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @ApiModelProperty(value = "工单ID", example = "1234", dataType = "java.lang.String")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ticketId;

    @ApiModelProperty(value = "工单编号", example = "WO202609081234")
    private String ticketNo;

    @ApiModelProperty(value = "工单标题", example = "无法访问公司内网")
    private String ticketTitle;

    @ApiModelProperty(value = "处理人ID", example = "2", dataType = "java.lang.String")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handlerId;

    @ApiModelProperty(value = "处理人姓名", example = "李明")
    private String handlerName;

    @ApiModelProperty(value = "分配时的综合评分", example = "92.4")
    private BigDecimal score;

    @ApiModelProperty(value = "分配方式：SYSTEM（系统分配）、MANUAL（人工分配）",
            example = "SYSTEM", allowableValues = "SYSTEM,MANUAL")
    private String assignedBy;

    @ApiModelProperty(value = "技能匹配分数", example = "100")
    private BigDecimal skillMatchScore;

    @ApiModelProperty(value = "负载分数", example = "58.3")
    private BigDecimal loadScore;

    @ApiModelProperty(value = "SLA分数", example = "98.6")
    private BigDecimal slaScore;

    @ApiModelProperty(value = "历史评分折算分数", example = "98")
    private BigDecimal ratingScore;

    @ApiModelProperty(value = "分配记录创建时间", example = "2026-09-08 09:15:00",
            dataType = "java.lang.String")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
