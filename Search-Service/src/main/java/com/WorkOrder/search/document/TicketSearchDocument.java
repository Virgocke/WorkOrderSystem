package com.WorkOrder.search.document;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 工单的搜索投影，与数据库实体及接口响应分离。
 * ID 使用字符串，时间必须携带偏移量；后续由同步层生成完整快照。
 */
@Getter
@Setter
public class TicketSearchDocument {

    /** 工单稳定 ID，同时作为 Elasticsearch 文档的 _id，保存时必须非空。 */
    private String ticketId;

    /** 工单业务编号，按 keyword 存储并支持精确匹配，保存时必须非空。 */
    private String ticketNo;

    /** 工单标题，使用 ik_max_word 建索引、ik_smart 查询，保存时必须非空。 */
    private String title;

    /** 工单描述，使用 ik_max_word 建索引、ik_smart 查询，可不提供。 */
    private String description;

    /** 工单分类 ID，以字符串保存，供分类精确过滤使用。 */
    private String categoryId;

    /** 工单优先级数值，沿用源业务定义，以整数保存，未提供时为空。 */
    private Integer priority;

    /** 工单状态编码，沿用源业务值，以 keyword 保存，供状态精确过滤使用。 */
    private String status;

    /** 创建人用户 ID，以字符串保存，可用于后续由服务端生成的权限过滤。 */
    private String creatorId;

    /** 当前处理人用户 ID，尚未分配时可为空，可用于处理人精确过滤。 */
    private String handlerId;

    /** SLA 状态编码，沿用源业务值，以 keyword 保存，供 SLA 状态过滤使用。 */
    private String slaStatus;

    /** 工单创建时间，保存时必须提供偏移量，以 ISO 8601 字符串写入并用于排序。 */
    private OffsetDateTime createdAt;

    /** 源工单的更新时间，保存时必须提供偏移量；此字段不承担乱序写入的版本仲裁。 */
    private OffsetDateTime updatedAt;
}
