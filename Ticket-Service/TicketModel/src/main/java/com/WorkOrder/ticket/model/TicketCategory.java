package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月13日 02:57
 * @description 工单分类数据库实体，包含分类的id，名称，父分类id，默认优先级，默认响应sla，默认解决sla，描述，创建时间
 */
@Data
@TableName("ticket_categories")
public class TicketCategory {
    private Long id;
    private String name;
    private Long parentId;
    private int defaultPriority;
    /** 响应时限（分钟）；null 表示使用系统默认值。 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer defaultResponseSla;
    /** 解决时限（分钟）；null 表示使用系统默认值。 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer defaultResolutionSla;
    private String description;
    /** 非数据库列，用于分类写入接口返回当前分类配置的技能。 */
    @TableField(exist = false)
    private List<Long> requiredSkillIds;
    // 创建时间，数据库自动生成，不用填写
    private LocalDateTime createdAt;
}
