package com.WorkOrder.assignment.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.time.LocalDateTime;

/** 系统配置响应，value为解析后的JSON，Long字段由公共配置序列化为字符串。 */
@Value
public class ConfigurationItem {
    String configKey;
    JsonNode value;
    String description;
    Long version;
    Long updatedBy;
    LocalDateTime updatedAt;
}
