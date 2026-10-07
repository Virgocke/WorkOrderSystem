package com.WorkOrder.search.model.admin;

import com.WorkOrder.search.model.*;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员查看的共享状态和运行证据，采集失败保留 unknown 而非伪造零值。
 */
@Data
public class TicketSearchAdminStatus {
    /**
     * 是否开启投影同步。
     */
    private boolean syncEnabled;
    /**
     * 本次实际通过主库、别名、映射检查后才为 true。
     */
    private boolean ready;
    /**
     * 主库控制记录，主库不可读时为空。
     */
    private TicketSearchControlState control;
    /**
     * 当前任务进度及恢复标识。
     */
    private TicketSearchImportTask task;
    /**
     * 独立对账进度及最后完整成功时间。
     */
    private TicketSearchReconciliationState reconciliation;
    /**
     * 按搜索 Topic 采集的 Outbox 和消费健康。
     */
    private TicketSearchRuntimeStatus runtime;
    /**
     * 主库或就绪检查的脱敏故障分类。
     */
    private String error;
}
