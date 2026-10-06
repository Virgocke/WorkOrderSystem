package com.WorkOrder.search.model.admin;

import lombok.Getter;
import lombok.Setter;

/** 单个搜索 Topic 队列的 Broker 最大位置与该消费组持久提交位置。 */
@Getter
@Setter
public class TicketSearchQueueStatus {
    private String brokerName;
    private Integer queueId;
    private TicketSearchDiagnosticState state;
    private Long brokerOffset;
    private Long committedOffset;
    private Long backlog;
    /** Broker 明确无提交记录且刚读取的队列末尾为零，因此没有消息待消费。 */
    private boolean emptyQueueWithoutCommittedOffset;
    /** 此队列是否分配给当前实例；其他队列仍按全组提交位置采集。 */
    private boolean localAssigned;
    private String error;
}
