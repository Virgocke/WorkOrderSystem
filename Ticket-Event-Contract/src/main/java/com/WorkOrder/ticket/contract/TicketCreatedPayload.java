package com.WorkOrder.ticket.contract;

import com.WorkOrder.messaging.contract.EventType;
import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单创建事件 V1 快照，供派单和 SLA 消费者独立使用。
 */
public final class TicketCreatedPayload {
    /**
     * 工单主键。
     */
    private final long ticketId;
    /**
     * 工单编号。
     */
    private final String ticketNo;
    /**
     * 可空的工单标题。
     */
    private final String ticketTitle;
    /**
     * 工单分类 ID。
     */
    private final long categoryId;
    /**
     * 工单优先级，1 为最高。
     */
    private final int priority;
    /**
     * 工单创建人 ID。
     */
    private final long creatorId;
    /**
     * 创建时间，包含时区偏移量。
     */
    private final OffsetDateTime createdAt;
    /**
     * 响应截止时间，包含时区偏移量。
     */
    private final OffsetDateTime responseDeadline;
    /**
     * 解决截止时间，包含时区偏移量。
     */
    private final OffsetDateTime resolutionDeadline;

    /**
     * 保存校验后的创建快照。
     *
     * @param ticketId 工单主键
     * @param ticketNo 工单编号
     * @param ticketTitle 可空的工单标题
     * @param categoryId 工单分类 ID
     * @param priority 工单优先级，1 为最高
     * @param creatorId 工单创建人 ID
     * @param createdAt 创建时间，包含时区偏移量
     * @param responseDeadline 响应截止时间，包含时区偏移量
     * @param resolutionDeadline 解决截止时间，包含时区偏移量
     */
    private TicketCreatedPayload(long ticketId, String ticketNo, String ticketTitle,
                                 long categoryId, int priority, long creatorId,
                                 OffsetDateTime createdAt, OffsetDateTime responseDeadline,
                                 OffsetDateTime resolutionDeadline) {
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.ticketTitle = ticketTitle;
        this.categoryId = categoryId;
        this.priority = priority;
        this.creatorId = creatorId;
        this.createdAt = createdAt;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /**
     * 校验创建状态、操作者、时间和聚合标识后读取 V1 快照。
     *
     * @param event 携带创建动作快照的 V1 工单领域事件
     * @return 通过信封、业务身份及创建事实校验的稳定事件载荷
     */
    public static TicketCreatedPayload from(WorkOrderEvent event) {
        JsonNode payload = TicketPayloadReader.payload(event, EventType.TICKET_CREATED);
        long ticketId = TicketPayloadReader.positiveLong(payload, "ticketId");
        String ticketNo = TicketPayloadReader.text(payload, "ticketNo");
        String ticketTitle = TicketPayloadReader.optionalText(payload, "ticketTitle");
        long categoryId = TicketPayloadReader.positiveLong(payload, "categoryId");
        int priority = TicketPayloadReader.integer(payload, "priority", 1, 4);
        long creatorId = TicketPayloadReader.positiveLong(payload, "creatorId");
        OffsetDateTime createdAt = TicketPayloadReader.time(payload, "createdAt");
        OffsetDateTime responseDeadline = TicketPayloadReader.time(payload, "responseDeadline");
        OffsetDateTime resolutionDeadline = TicketPayloadReader.time(payload, "resolutionDeadline");
        if (!"PENDING_ASSIGN".equals(TicketPayloadReader.text(payload, "status"))
                || responseDeadline.isBefore(createdAt)
                || resolutionDeadline.isBefore(createdAt)) {
            throw new IllegalArgumentException("创建事件状态或 SLA 截止时间无效");
        }
        TicketPayloadReader.identity(event, ticketId, creatorId);
        TicketPayloadReader.occurredAt(event, createdAt);
        return new TicketCreatedPayload(ticketId, ticketNo, ticketTitle, categoryId,
                priority, creatorId, createdAt, responseDeadline, resolutionDeadline);
    }

    /**
     * 获取工单主键。
     *
     * @return 工单主键
     */
    public long getTicketId() { return ticketId; }
    /**
     * 获取工单编号。
     *
     * @return 工单编号
     */
    public String getTicketNo() { return ticketNo; }
    /**
     * 获取可空的工单标题。
     *
     * @return 可空的工单标题
     */
    public String getTicketTitle() { return ticketTitle; }
    /**
     * 获取工单分类 ID。
     *
     * @return 工单分类 ID
     */
    public long getCategoryId() { return categoryId; }
    /**
     * 获取工单优先级，1 为最高。
     *
     * @return 优先级，1 至 4
     */
    public int getPriority() { return priority; }
    /**
     * 获取工单创建人 ID。
     *
     * @return 创建人 ID
     */
    public long getCreatorId() { return creatorId; }
    /**
     * 获取创建时间，包含时区偏移量。
     *
     * @return 带时区偏移量的创建时间
     */
    public OffsetDateTime getCreatedAt() { return createdAt; }
    /**
     * 获取响应截止时间，包含时区偏移量。
     *
     * @return 带时区偏移量的响应截止时间
     */
    public OffsetDateTime getResponseDeadline() { return responseDeadline; }
    /**
     * 获取解决截止时间，包含时区偏移量。
     *
     * @return 带时区偏移量的解决截止时间
     */
    public OffsetDateTime getResolutionDeadline() { return resolutionDeadline; }
}
