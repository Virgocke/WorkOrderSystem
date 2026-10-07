package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 自动升级请求只携带工单 ID，执行时必须重新检查最新配置和工单事实。
 */
public final class EscalationRequestedPayload {
    /**
     * 禁止实例化契约工具类。
     */
    private EscalationRequestedPayload() { }

    /**
     * 校验系统请求信封并返回正整数工单 ID。
     *
     * @param event 系统发出的 V1 升级检查请求，当前升级条件由下游回源复核
     * @return 通过来源、聚合身份和系统操作人校验的正整数工单 ID
     */
    public static long ticketId(WorkOrderEvent event) {
        if (event == null || !EventType.ESCALATION_REQUESTED.name().equals(event.getEventType())
                || event.getEventVersion() != 1 || !"TICKET".equals(event.getAggregateType())
                || !"SYSTEM".equals(event.getActorId()) || event.getOccurredAt() == null) {
            throw new IllegalArgumentException("自动升级请求信封无效");
        }
        JsonNode id = event.getPayload() == null ? null : event.getPayload().get("ticketId");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0
                || !String.valueOf(id.longValue()).equals(event.getAggregateId())) {
            throw new IllegalArgumentException("自动升级请求工单ID无效");
        }
        return id.longValue();
    }
}
