package com.WorkOrder.dashboard.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/** 管理员审计查询响应；姓名取当前用户信息。 */
@Data
public class ConfigurationAuditItem {
    /** 当前审计记录唯一标识。 */
    private Long id;
    /** 系统配置键；筛选时精确匹配。 */
    private String configKey;
    /** 修改前的原始配置 JSON。 */
    private String beforeValue;
    /** 修改后的原始配置 JSON。 */
    private String afterValue;
    /** 保存后的配置版本，以字符串输出避免精度丢失。 */
    private String version;
    /** 操作人用户 ID；系统动作可为空。 */
    private Long operatorId;
    /** 操作人当前姓名，账号缺失时显示用户 ID。 */
    private String operatorName;
    /** 操作发生时间，按项目本地时间输出。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
