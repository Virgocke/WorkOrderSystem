package com.WorkOrder.model.search;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 搜索查询共享条件，可作为服务间调用的请求模型。
 * 业务入口负责权限校验，创建人及处理人等权限过滤必须由服务端生成。
 */
@Getter
@Setter
public class TicketSearchQuery {

    /** 最多 500 个字符；去除首尾空白后精确匹配编号或用 ik_smart 查询标题、描述，空白时不限制。 */
    private String keyword;

    /** 分类 ID 精确过滤条件，空值或空白表示不限制分类。 */
    private String categoryId;

    /** 优先级数值精确过滤条件，null 表示不限制优先级。 */
    private Integer priority;

    /** 工单状态编码精确过滤条件，空值或空白表示不限制状态。 */
    private String status;

    /** 创建人用户 ID 精确过滤条件；接入业务时应按登录身份在服务端生成。 */
    private String creatorId;

    /** 当前处理人用户 ID 精确过滤条件；接入业务时应按登录身份在服务端生成。 */
    private String handlerId;

    /** SLA 状态编码精确过滤条件，空值或空白表示不限制 SLA 状态。 */
    private String slaStatus;

    /** 创建时间下限，包含边界；业务入口应明确时区后转换为带偏移量的时间。 */
    private OffsetDateTime start;

    /** 创建时间上限，包含边界；可以单独提供，但不能早于下限。 */
    private OffsetDateTime end;

    /** 排序方式：deadline 为首次响应截止时间升序，priority 为优先级倒序，createdAt 或 null 为创建时间倒序。 */
    private String sort;

    /** 从 1 开始的页码，默认 1；与每页数量的乘积不能超过基础查询窗口 10000。 */
    private int page = 1;

    /** 每页数量，默认 20，允许 1 至 100。 */
    private int pageSize = 20;
}
