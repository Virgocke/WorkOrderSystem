package com.WorkOrder.messaging.contract;

/** 首期统一维护的领域事件类型。 */
public enum EventType {
    /** 工单创建事件 */
    TICKET_CREATED,
    /** 工单指派事件 */
    TICKET_ASSIGNED,
    /** 工单转交事件 */
    TICKET_TRANSFERRED,
    /** 工单回复事件 */
    TICKET_REPLIED,
    /** 工单提醒事件 */
    TICKET_REMINDED,
    /** 工单响应事件 */
    TICKET_RESPONDED,
    /** 工单解决事件 */
    TICKET_RESOLVED,
    /** 工单关闭事件 */
    TICKET_CLOSED,
    /** 工单取消事件 */
    TICKET_CANCELLED,
    /** 工单升级事件 */
    TICKET_ESCALATED,
    /** SLA 监控请求工单服务重新判断自动升级。 */
    ESCALATION_REQUESTED,
    /** 自动派单候选提议事件 */
    ASSIGNMENT_PROPOSED,
    /** 工单指派失败事件 */
    ASSIGNMENT_FAILED,
    /** SLA警告事件 */
    SLA_WARNING,
    /** SLA breaches事件 */
    SLA_BREACHED,
    /** SLA恢复事件 */
    SLA_RECOVERED
}
