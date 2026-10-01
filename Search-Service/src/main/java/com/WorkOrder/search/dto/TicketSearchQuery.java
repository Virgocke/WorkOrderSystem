package com.WorkOrder.search.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 内部查询条件，不作为公开接口的请求模型。
 * 后续接入业务时，创建人及处理人等权限过滤必须由服务端生成。
 */
@Getter
@Setter
public class TicketSearchQuery {

    /** 最多 500 个字符；去除首尾空白后匹配编号或分词匹配标题、描述，空白时不限制关键字。 */
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

    /** 从 1 开始的页码，默认 1；与每页数量的乘积不能超过基础查询窗口 10000。 */
    private int page = 1;

    /** 每页数量，默认 20，允许 1 至 100。 */
    private int pageSize = 20;
}
