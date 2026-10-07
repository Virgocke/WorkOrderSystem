package com.WorkOrder.messaging.consumer;

import com.WorkOrder.messaging.contract.WorkOrderEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 消费幂等日志写入器。
 */
public class ConsumeLogMapper {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建消费日志数据访问组件。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public ConsumeLogMapper(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 尝试登记一次事件消费。
     * 数据库唯一键 consumer_group + event_id 负责并发裁决，重复事件返回 0。
     *
     * @param consumerGroup 与 eventId 组成消费幂等唯一键的业务消费组
     * @param event 提供稳定业务 eventId 和事件类型的统一信封
     * @param topic 消息来源 Topic
     * @param tag 消息 Tag
     * @return 消费日志首次登记为 1；唯一键重复时为 0，不使用 Broker 消息 ID 去重
     */
    public int insertIgnore(String consumerGroup, WorkOrderEvent event, String topic, String tag) {
        String sql = "INSERT IGNORE INTO message_consume_log "
                + "(consumer_group, event_id, topic, tag, event_type) "
                + "VALUES (:consumerGroup, :eventId, :topic, :tag, :eventType)";
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("consumerGroup", consumerGroup)
                .addValue("eventId", event.getEventId())
                .addValue("topic", topic)
                .addValue("tag", tag)
                .addValue("eventType", event.getEventType()));
    }
}
