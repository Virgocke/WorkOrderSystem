package com.WorkOrder.assignment.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 系统配置数据库记录。
 */
@Data
public class Configuration {
    private String configKey;
    private String configValue;
    private String description;
    private Long version;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
