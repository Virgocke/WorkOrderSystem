package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 自动派单提议 V1 快照；只有 Ticket-Service 可以确认最终分配。
 */
public final class AssignmentProposedPayload {
    /**
     * 创建事件 ID，用于追踪提议来源。
     */
    private final String createdEventId;
    /**
     * 工单主键。
     */
    private final long ticketId;
    /**
     * 候选处理人用户 ID。
     */
    private final long handlerId;
    /**
     * 推荐时的综合得分，取值 0 至 100。
     */
    private final BigDecimal score;
    /**
     * 推荐时的技能分，取值 0 至 100。
     */
    private final BigDecimal skillMatchScore;
    /**
     * 推荐时的负载分，取值 0 至 100。
     */
    private final BigDecimal loadScore;
    /**
     * 推荐时的 SLA 分，取值 0 至 100。
     */
    private final BigDecimal slaScore;
    /**
     * 推荐时的评分分，取值 0 至 100。
     */
    private final BigDecimal ratingScore;
    /**
     * 提议时间，包含时区偏移量。
     */
    private final OffsetDateTime proposedAt;

    /**
     * 事件 ID 的固定格式。
     */
    private static final Pattern EVENT_ID = Pattern.compile("[0-9a-fA-F]{32}");

    /**
     * 保存已校验的提议快照。
     *
     * @param createdEventId 创建事件 ID，用于追踪提议来源
     * @param ticketId 工单主键
     * @param handlerId 候选处理人用户 ID
     * @param score 推荐时的综合得分，取值 0 至 100
     * @param skillMatchScore 推荐时的技能分，取值 0 至 100
     * @param loadScore 推荐时的负载分，取值 0 至 100
     * @param slaScore 推荐时的 SLA 分，取值 0 至 100
     * @param ratingScore 推荐时的评分分，取值 0 至 100
     * @param proposedAt 提议时间，包含时区偏移量
     */
    private AssignmentProposedPayload(String createdEventId, long ticketId, long handlerId,
                                      BigDecimal score, BigDecimal skillMatchScore,
                                      BigDecimal loadScore, BigDecimal slaScore,
                                      BigDecimal ratingScore, OffsetDateTime proposedAt) {
        this.createdEventId = createdEventId;
        this.ticketId = ticketId;
        this.handlerId = handlerId;
        this.score = score;
        this.skillMatchScore = skillMatchScore;
        this.loadScore = loadScore;
        this.slaScore = slaScore;
        this.ratingScore = ratingScore;
        this.proposedAt = proposedAt;
    }

    /**
     * 校验来源、候选人、评分和系统操作人后提取 V1 快照。
     *
     * @param event 携带分配候选提议动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及分配候选提议事实校验的稳定事件载荷
     */
    public static AssignmentProposedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.ASSIGNMENT_PROPOSED);
        String createdEventId = TicketPayloadReader.text(payload, "createdEventId");
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        long handlerId = TicketPayloadReader.positiveLong(payload, "handlerId");
        BigDecimal score = score(payload, "score");
        BigDecimal skillMatchScore = score(payload, "skillMatchScore");
        BigDecimal loadScore = score(payload, "loadScore");
        BigDecimal slaScore = score(payload, "slaScore");
        BigDecimal ratingScore = score(payload, "ratingScore");
        OffsetDateTime proposedAt = TicketPayloadReader.time(payload, "proposedAt");
        if (!EVENT_ID.matcher(createdEventId).matches()
                || !String.valueOf(ticketId).equals(event.getAggregateId())
                || !"SYSTEM".equals(event.getActorId())) {
            throw new IllegalArgumentException("自动派单提议来源或事件信封无效");
        }
        TicketPayloadReader.occurredAt(event, proposedAt);
        return new AssignmentProposedPayload(createdEventId, ticketId, handlerId,
                score, skillMatchScore, loadScore, slaScore, ratingScore, proposedAt);
    }

    /**
     * 读取 0 至 100 的十进制评分。
     *
     * @param payload 事件载荷
     * @param field 待读取或校验的字段名
     * @return 通过数值校验且位于 0 至 100 闭区间内的评分
     */
    private static BigDecimal score(JsonNode payload, String field) {
        JsonNode value = TicketPayloadReader.required(payload, field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException("payload." + field + " 必须为数值");
        }
        BigDecimal score = value.decimalValue();
        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("payload." + field + " 超出评分范围");
        }
        return score;
    }

    /**
     * 获取创建事件 ID，用于追踪提议来源。
     *
     * @return 来源创建事件 ID
     */
    public String getCreatedEventId() { return createdEventId; }
    /**
     * 获取工单主键。
     *
     * @return 工单主键
     */
    public long getTicketId() { return ticketId; }
    /**
     * 获取候选处理人用户 ID。
     *
     * @return 算法提出的候选处理人 ID，最终派单仍由工单服务决定
     */
    public long getHandlerId() { return handlerId; }
    /**
     * 获取推荐时的综合得分，取值 0 至 100。
     *
     * @return 综合得分
     */
    public BigDecimal getScore() { return score; }
    /**
     * 获取推荐时的技能分，取值 0 至 100。
     *
     * @return 技能分
     */
    public BigDecimal getSkillMatchScore() { return skillMatchScore; }
    /**
     * 获取推荐时的负载分，取值 0 至 100。
     *
     * @return 负载分
     */
    public BigDecimal getLoadScore() { return loadScore; }
    /**
     * 获取推荐时的 SLA 分，取值 0 至 100。
     *
     * @return SLA 分
     */
    public BigDecimal getSlaScore() { return slaScore; }
    /**
     * 获取推荐时的评分分，取值 0 至 100。
     *
     * @return 历史评分分
     */
    public BigDecimal getRatingScore() { return ratingScore; }
    /**
     * 获取提议时间，包含时区偏移量。
     *
     * @return 带时区偏移量的提议时间
     */
    public OffsetDateTime getProposedAt() { return proposedAt; }
}
