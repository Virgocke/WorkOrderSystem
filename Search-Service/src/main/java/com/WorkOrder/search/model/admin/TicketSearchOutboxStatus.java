package com.WorkOrder.search.model.admin;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 只统计配置搜索 Topic、搜索事件类型和 ticket-service 来源的 Outbox。 */
@Getter
@Setter
public class TicketSearchOutboxStatus {
    /** 未能读取业务库时为 UNKNOWN，所有数量保持 null。 */
    private TicketSearchDiagnosticState state;
    private Long newCount;
    private Long retryCount;
    private Long sendingCount;
    /** SENDING 中锁时间缺失或已经超过诊断阈值的数量。 */
    private Long staleSendingCount;
    /** 保留的终态发送失败；对账已修复时不直接阻止发布。 */
    private Long deadCount;
    /** 非协议状态数量，避免把异常记录静默当成已发送。 */
    private Long unknownStatusCount;
    /** 活跃消息中最早创建时间；属于数据库 DATETIME 的服务器时间。 */
    private LocalDateTime oldestActiveCreatedAt;
    /** 数据库执行聚合查询的时间；属于数据库 DATETIME 的服务器时间。 */
    private LocalDateTime collectedAt;
    /** 脱敏失败类型，不携带 SQL、消息正文或数据库连接信息。 */
    private String error;

    /** @return 待发送、等待重试及发送中的总数量；未知时返回 null */
    public Long getActiveCount() {
        if (newCount == null || retryCount == null || sendingCount == null) {
            return null;
        }
        return Math.addExact(Math.addExact(newCount, retryCount), sendingCount);
    }
}
