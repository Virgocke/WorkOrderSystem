package com.WorkOrder.dashboard.dto;

import com.WorkOrder.model.handler.HandlerProfile;
import com.WorkOrder.model.ticket.TicketResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 处理人工作台响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandlerWorkbenchDto {

    /** 当前处理人待首次响应的工单数量。 */
    private long pendingCount;

    /** 当前处理人即将超时、已超时或已升级的未终结工单数量。 */
    private long nearTimeoutCount;

    /** 当前处理人今天解决或关闭的工单数量，同一工单只计一次。 */
    private long doneToday;

    /** 当前处理人从工单创建到解决的历史平均时长，单位为分钟。 */
    private BigDecimal avgResolutionMinutes;

    /** 当前处理人未终结工单的状态分布。 */
    private List<DashboardCountDto> byStatus;

    /** 最接近响应或解决时限的工单，最多八条。 */
    private List<TicketResponse> upcoming;

    /** 当前处理人的档案及技能。 */
    private HandlerProfile profile;
}
