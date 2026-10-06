package com.WorkOrder.search.model.admin;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 本机真实消费状态与指定 Topic 的全组 Broker 持久 offset 观测，不能证明其他实例存活。 */
@Getter
@Setter
public class TicketSearchConsumerStatus {
    private TicketSearchDiagnosticState state;
    /** 容器、客户端状态及暂停标记共同确认的当前实例运行状态。 */
    private Boolean localRunning;
    private String clientId;
    private Integer localAssignedQueueCount;
    private Integer groupQueueCount;
    /** 任一队列无法读取时保持 null，不能使用部分队列的合计。 */
    private Long groupBacklog;
    private List<TicketSearchQueueStatus> queues = new ArrayList<>();
    /** 说明观测范围不包含源端部署证明、其他实例存活或 retry/DLQ Topic。 */
    private String scope = "配置搜索Topic的全部路由队列及该组Broker持久offset；运行标记仅为本机；不包含retry/DLQ或源端部署证明";
    private String error;
}
