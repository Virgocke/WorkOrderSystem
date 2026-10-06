package com.WorkOrder.search.model;

import lombok.Data;
import java.time.LocalDateTime;

/** 与历史导入进度分开保存的周期对账轮次。 */
@Data
public class TicketSearchReconciliationState {
    /** 固定主键 1。 */
    private Long id;
    /** 本轮固定代次。 */
    private Long generation;
    /** 每轮都重新取得的有限上界，null 表示尚未开始本轮。 */
    private Long scanUpperId;
    /** 本轮已确认游标。 */
    private Long lastTicketId;
    /** 当前扫描令牌。 */
    private String leaseToken;
    /** 租约截止时间。 */
    private LocalDateTime leaseUntil;
    /** 累计完整轮数。 */
    private Long completedRounds;
    /** 最近一次完整对账成功时间。 */
    private LocalDateTime lastFullSuccessAt;
    /** 最近故障分类。 */
    private String lastError;
}
