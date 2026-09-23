package com.WorkOrder.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年09月23日 19:26
 * @description 技能标签实体类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillTagRecord {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
