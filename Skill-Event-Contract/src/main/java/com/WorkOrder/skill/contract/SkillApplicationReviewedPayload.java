package com.WorkOrder.skill.contract;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 技能审核 V1 快照契约，生产端和消费端使用相同校验。
 */
public final class SkillApplicationReviewedPayload {
    /**
     * 工具契约类不允许实例化。
     */
    private SkillApplicationReviewedPayload() { }

    /**
     * 校验业务身份、状态、类型和时间；返回不依赖当前申请状态的快照。
     *
     * @param event SKILL_APPLICATION_REVIEWED V1 事件，携带审核时的申请快照
     * @return 通过申请身份、审核人、状态、类型及时间校验的原始 payload 节点
     */
    public static JsonNode from(WorkOrderEvent event) {
        if (event == null || !"SKILL_APPLICATION_REVIEWED".equals(event.getEventType())
                || event.getEventVersion() != 1 || !"SKILL_APPLICATION".equals(event.getAggregateType())
                || event.getPayload() == null || !event.getPayload().isObject()) {
            throw new IllegalArgumentException("技能审核事件信封无效");
        }
        JsonNode p = event.getPayload();
        for (String key : new String[]{"applicationId", "applicantId", "skillId", "reviewerId"}) {
            if (!p.path(key).isIntegralNumber() || !p.path(key).canConvertToLong() || p.path(key).longValue() <= 0) {
                throw new IllegalArgumentException("技能审核事件 ID 无效：" + key);
            }
        }
        if (!p.path("applicationId").asText().equals(event.getAggregateId())
                || !p.path("reviewerId").asText().equals(event.getActorId())
                || !("APPROVED".equals(p.path("status").asText()) || "REJECTED".equals(p.path("status").asText()))
                || !("ADD".equals(p.path("type").asText()) || "ADJUST".equals(p.path("type").asText())
                || "REMOVE".equals(p.path("type").asText()))
                || !p.path("skillName").isTextual() || p.path("skillName").asText().trim().isEmpty()
                || (!p.path("reviewComment").isNull() && (!p.path("reviewComment").isTextual()
                || p.path("reviewComment").asText().length() > 300))) {
            throw new IllegalArgumentException("技能审核事件快照无效");
        }
        OffsetDateTime at = OffsetDateTime.parse(p.path("reviewedAt").asText());
        if (event.getOccurredAt() == null || !event.getOccurredAt().toInstant().equals(at.toInstant())) {
            throw new IllegalArgumentException("技能审核事件时间不一致");
        }
        return p;
    }
}
