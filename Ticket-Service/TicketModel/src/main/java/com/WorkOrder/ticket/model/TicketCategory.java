package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
    private int defaultResponseSla;
    private int defaultResolutionSla;
    private String description;
    // 创建时间，数据库自动生成，不用填写
    private String createdAt;
}
