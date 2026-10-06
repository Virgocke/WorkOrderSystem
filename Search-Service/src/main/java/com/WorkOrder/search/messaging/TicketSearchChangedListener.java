package com.WorkOrder.search.messaging;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import com.WorkOrder.search.model.TicketSearchWriteOutcome;
import com.WorkOrder.search.service.TicketProjectionService;
import com.WorkOrder.ticket.contract.TicketSearchChangedPayload;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;

/** 回源写入完成后才正常返回；异常交给 RocketMQ 重投，不记录本地跳过日志。 */
@RocketMQMessageListener(
        topic = "${work-order.messaging.ticket-search-topic:wo-ticket-search-event}",
        selectorExpression = "CHANGED",
        consumerGroup = "${work-order.elasticsearch.ticket-sync.consumer-group:search-ticket-projection-v1}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 16)
public class TicketSearchChangedListener implements RocketMQListener<WorkOrderEvent>, RocketMQPushConsumerLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketSearchChangedListener.class);
    private static final Pattern EVENT_ID = Pattern.compile("[0-9a-fA-F]{32}");
    private final TicketProjectionService projectionService;

    /** @param projectionService 统一回源及版本写入组件 */
    public TicketSearchChangedListener(TicketProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    /**
     * 新消费组没有持久位点时从保留消息开头消费，避免第一次队列分配前的增量被跳过。
     * 已有消费组仍从 Broker 持久位点继续；历史重复由源版本仲裁安全覆盖。
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    }

    /** 校验信封与契约；只有 APPLIED 或 COVERED 才允许确认消费。 */
    @Override
    public void onMessage(WorkOrderEvent event) {
        if (event == null || event.getEventId() == null
                || !EVENT_ID.matcher(event.getEventId()).matches() || event.getOccurredAt() == null) {
            throw new IllegalArgumentException("工单搜索事件 ID 或发生时间无效");
        }
        TicketSearchChangedPayload payload = TicketSearchChangedPayload.from(event);
        try {
            // 事件只通知“工单变了”，投影服务回源取完整现状；乱序到达也允许直接写入更高版本。
            TicketSearchWriteOutcome outcome = projectionService.synchronize(
                    payload.getTicketId(), event.getAggregateVersion());
            if (outcome != TicketSearchWriteOutcome.APPLIED && outcome != TicketSearchWriteOutcome.COVERED) {
                throw new IllegalStateException("工单搜索写入未返回有效结果");
            }
            LOGGER.debug("工单搜索事件处理完成 eventId={} ticketId={} requestedVersion={} outcome={}",
                    event.getEventId(), payload.getTicketId(), event.getAggregateVersion(), outcome);
        } catch (IOException | RuntimeException exception) {
            // 抛异常让 RocketMQ 重投，不能吞掉失败后正常返回，否则消息会被当作消费成功。
            // 不输出完整事件、工单内容或 ES 错误正文，避免业务数据进入日志。
            LOGGER.warn("工单搜索事件处理失败 eventId={} ticketId={} requestedVersion={} failureType={}",
                    event.getEventId(), payload.getTicketId(), event.getAggregateVersion(),
                    exception.getClass().getSimpleName());
            throw new IllegalStateException("工单搜索事件处理失败，eventId=" + event.getEventId(), exception);
        }
    }
}
