package com.WorkOrder.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 审计日志的搜索投影；源库仍负责响应字段，ES 查询仅返回稳定来源标识。
 */
@Getter
@Setter
public class AuditSearchDocument {

    /**
     * 稳定标识，格式为来源与源日志 ID 拼接，例如 OPERATION:123。
     */
    private String id;

    /**
     * 日志种类，工单为 TICKET，系统配置为 CONFIGURATION。
     */
    private String kind;

    /**
     * 源日志表类型：OPERATION、STATUS 或 CONFIGURATION。
     */
    private String source;

    /**
     * 源日志表中的数值主键，避免字符串排序与 SQL 数值排序不同。
     */
    private Long sourceId;

    /**
     * 工单日志所属的工单 ID，配置日志不提供。
     */
    private Long ticketId;

    /**
     * 操作人 ID，未提供时索引统一保存为 0，表示系统。
     */
    private Long operatorId;

    /**
     * 工单操作类型或状态变更动作，采用字面子串搜索。
     */
    private String action;

    /**
     * 工单审计内容，采用字面子串搜索。
     */
    private String content;

    /**
     * 系统配置键，支持字面子串搜索与独立的精确筛选。
     */
    private String configKey;

    /**
     * 配置变更前的值，采用字面子串搜索。
     */
    private String beforeValue;

    /**
     * 配置变更后的值，采用字面子串搜索。
     */
    private String afterValue;

    /**
     * 源库本地创建时间，按 Asia/Shanghai 转为毫秒时间戳；历史 NULL 时间保留为缺失字段。
     */
    private LocalDateTime createdAt;
}
