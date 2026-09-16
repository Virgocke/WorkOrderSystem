package com.WorkOrder.handler.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月16日 23:41
 * @description 分配记录实体类
 */
@Data
@TableName("assignment_records")
public class AssignmentRecord {
    private Long id;
    private Long ticketId;
    private Long handlerId;
    /** 分配时的综合评分 */
    private BigDecimal score;
    /** 分配方式，SYSTEM/MANUAL */
    private String assignedBy;
    /** 技能匹配分数 */
    private BigDecimal skillMatchScore;
    /** 加载分数 */
    private BigDecimal loadScore;
    /** SLA分数 */
    private BigDecimal slaScore;
    /** 评分分数 */
    private BigDecimal ratingScore;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
