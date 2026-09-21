package com.WorkOrder.user.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月22日 01:22
 * @description 处理员技能表
 */
@Data
@TableName("handler_skills")
public class HandlerSkill {
    private Long id;
    private Long handlerId;
    private Long skillTagId;
    private int proficiency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
