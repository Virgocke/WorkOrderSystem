package com.WorkOrder.messaging.outbox;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 使用短事务实现 Outbox 插入、认领和状态迁移。
 */
public class OutboxMapper {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建基于命名参数 JDBC 的 Outbox 数据访问组件。
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public OutboxMapper(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入一条初始 Outbox 记录。该操作会加入调用方现有事务。
     *
     * @param event 待保存的 Outbox 记录
     * @return 受影响行数
     */
    public int insert(OutboxEvent event) {
        String sql = "INSERT INTO message_outbox "
                + "(event_id, source_service, aggregate_type, aggregate_id, aggregate_version, "
                + "event_type, event_version, topic, tag, message_key, payload, status, retry_count, next_retry_at) "
                + "VALUES (:eventId, :sourceService, :aggregateType, :aggregateId, :aggregateVersion, "
                + ":eventType, :eventVersion, :topic, :tag, :messageKey, :payload, :status, 0, CURRENT_TIMESTAMP(3))";
        return jdbcTemplate.update(sql, parameters(event));
    }

    /**
     * 在短事务内认领一批到期消息。
     * 查询使用 FOR UPDATE SKIP LOCKED，避免多个 Relay 实例认领同一记录；
     * 返回前记录已经更新为 SENDING，方法结束后事务提交。
     *
     * @param sourceService 生产服务标识及 Outbox 扫描隔离键
     * @param instanceId 本次领取后保存到 locked_by 的 Relay 实例标识
     * @param batchSize 此次短事务最大认领记录数
     * @return 已更新为 SENDING 且归当前实例持有的到期记录，按主键升序；无可领取任务时为空列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OutboxEvent> claimAvailable(String sourceService, String instanceId, int batchSize) {
        String selectSql = "SELECT id FROM message_outbox "
                + "WHERE source_service = :sourceService AND status IN ('NEW', 'RETRY') "
                + "AND next_retry_at <= CURRENT_TIMESTAMP(3) ORDER BY id LIMIT :batchSize "
                + "FOR UPDATE SKIP LOCKED";
        // 准备查询参数
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceService", sourceService)
                .addValue("batchSize", batchSize);
        // 查询到期消息
        List<Long> ids = jdbcTemplate.queryForList(selectSql, parameters, Long.class);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        // 准备认领参数并更新消息状态
        String updateSql = "UPDATE message_outbox SET status = 'SENDING', locked_by = :instanceId, "
                + "locked_at = CURRENT_TIMESTAMP(3) WHERE id IN (:ids) AND status IN ('NEW', 'RETRY')";
        parameters.addValue("instanceId", instanceId).addValue("ids", ids);
        jdbcTemplate.update(updateSql, parameters);

        // 查询认领结果
        String claimedSql = "SELECT * FROM message_outbox WHERE id IN (:ids) "
                + "AND status = 'SENDING' AND locked_by = :instanceId ORDER BY id";
        return jdbcTemplate.query(claimedSql, parameters, new OutboxRowMapper());
    }

    /**
     * 将当前实例持有的 SENDING 记录标记为 SENT。
     *
     * @param id Outbox 内部主键
     * @param instanceId 当前 Relay 实例标识
     * @param rocketMqMessageId Broker 返回的消息 ID
     * @return 受影响行数；返回 0 表示记录状态或锁持有者已经变化
     */
    public int markSent(long id, String instanceId, String rocketMqMessageId) {
        String sql = "UPDATE message_outbox SET status = 'SENT', rocketmq_msg_id = :messageId, "
                + "sent_at = CURRENT_TIMESTAMP(3), locked_by = NULL, locked_at = NULL, last_error = NULL "
                + "WHERE id = :id AND status = 'SENDING' AND locked_by = :instanceId";
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("instanceId", instanceId)
                .addValue("messageId", rocketMqMessageId));
    }

    /**
     * 记录发送失败并计算下一次指数退避时间。
     * 达到最大失败次数时进入 DEAD，否则进入 RETRY。
     *
     * @param event 当前实例已领取的 Outbox 快照，包含旧 retryCount
     * @param instanceId 当前持有 SENDING 记录的 Relay 实例标识
     * @param maxRetries 进入 DEAD 前允许的最大失败次数
     * @param error 此次发送失败摘要，落库前限制为 1000 字符
     * @return 状态和锁持有者仍匹配时更新 1 行；已被恢复或其他实例接管时为 0
     */
    public int markFailed(OutboxEvent event, String instanceId, int maxRetries, String error) {
        int failureCount = event.getRetryCount() + 1;
        boolean dead = failureCount >= maxRetries;
        long baseDelaySeconds = Math.min(1L << Math.min(failureCount, 30), 600L);
        long delaySeconds = baseDelaySeconds + ThreadLocalRandom.current().nextInt(6);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        String sql = "UPDATE message_outbox SET status = :status, retry_count = retry_count + 1, "
                + "next_retry_at = :nextRetryAt, locked_by = NULL, locked_at = NULL, last_error = :lastError "
                + "WHERE id = :id AND status = 'SENDING' AND locked_by = :instanceId";
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", event.getId())
                .addValue("instanceId", instanceId)
                .addValue("status", dead ? OutboxStatus.DEAD.name() : OutboxStatus.RETRY.name())
                .addValue("nextRetryAt", Timestamp.valueOf(nextRetryAt))
                .addValue("lastError", abbreviate(error, 1000)));
    }

    /**
     * 把锁定时间早于阈值的 SENDING 记录恢复为 RETRY。
     *
     * @param sourceService 当前生产服务标识
     * @param lockedBefore 判断发送锁超时的时间阈值
     * @return 恢复的记录数量
     */
    public int recoverStale(String sourceService, LocalDateTime lockedBefore) {
        String sql = "UPDATE message_outbox SET status = 'RETRY', next_retry_at = CURRENT_TIMESTAMP(3), "
                + "locked_by = NULL, locked_at = NULL, last_error = '发送锁超时，已由恢复任务重新入队' "
                + "WHERE source_service = :sourceService AND status = 'SENDING' AND locked_at < :lockedBefore";
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sourceService", sourceService)
                .addValue("lockedBefore", Timestamp.valueOf(lockedBefore)));
    }

    /**
     * 统计等待发送的 NEW 和 RETRY 记录数量。
     *
     * @param sourceService 生产服务标识
     * @return 当前服务所有 NEW 或 RETRY 记录数，包括尚未到 next_retry_at 的记录
     */
    public long countPending(String sourceService) {
        return count("status IN ('NEW', 'RETRY')", sourceService);
    }

    /**
     * 统计需要人工处理的 DEAD 记录数量。
     *
     * @param sourceService 生产服务标识
     * @return DEAD 数量
     */
    public long countDead(String sourceService) {
        return count("status = 'DEAD'", sourceService);
    }

    /**
     * 统计超过发送锁时限但仍处于 SENDING 的记录数量。
     *
     * @param sourceService 生产服务标识
     * @param lockedBefore 判断超时的时间阈值
     * @return 超时发送记录数量
     */
    public long countStaleSending(String sourceService, LocalDateTime lockedBefore) {
        String sql = "SELECT COUNT(*) FROM message_outbox WHERE source_service = :sourceService "
                + "AND status = 'SENDING' AND locked_at < :lockedBefore";
        Long result = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("sourceService", sourceService)
                .addValue("lockedBefore", Timestamp.valueOf(lockedBefore)), Long.class);
        return result == null ? 0L : result;
    }

    /**
     * 查询最早一条待发送记录的创建时间，用于计算积压时长。
     *
     * @param sourceService 生产服务标识
     * @return 当前服务 NEW 或 RETRY 记录的最早创建时间；无待发送记录时为 null
     */
    public LocalDateTime findOldestPendingCreatedAt(String sourceService) {
        String sql = "SELECT MIN(created_at) FROM message_outbox WHERE source_service = :sourceService "
                + "AND status IN ('NEW', 'RETRY')";
        Timestamp timestamp = jdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource("sourceService", sourceService), Timestamp.class);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * 按固定的内部状态条件执行计数查询。
     *
     * @param condition 由调用方内部提供的 SQL 条件，不接受外部输入
     * @param sourceService 生产服务标识
     * @return 符合条件的记录数量
     */
    private long count(String condition, String sourceService) {
        String sql = "SELECT COUNT(*) FROM message_outbox WHERE source_service = :sourceService AND " + condition;
        Long result = jdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource("sourceService", sourceService), Long.class);
        return result == null ? 0L : result;
    }

    /**
     * 将 Outbox 模型转换为插入语句使用的命名参数。
     *
     * @param event Outbox 模型
     * @return JDBC 命名参数集合
     */
    private MapSqlParameterSource parameters(OutboxEvent event) {
        return new MapSqlParameterSource()
                .addValue("eventId", event.getEventId())
                .addValue("sourceService", event.getSourceService())
                .addValue("aggregateType", event.getAggregateType())
                .addValue("aggregateId", event.getAggregateId())
                .addValue("aggregateVersion", event.getAggregateVersion())
                .addValue("eventType", event.getEventType())
                .addValue("eventVersion", event.getEventVersion())
                .addValue("topic", event.getTopic())
                .addValue("tag", event.getTag())
                .addValue("messageKey", event.getMessageKey())
                .addValue("payload", event.getPayload())
                .addValue("status", event.getStatus().name());
    }

    /**
     * 截断错误摘要，防止超过数据库 last_error 字段上限。
     *
     * @param value 原始错误摘要
     * @param maxLength 最大字符数
     * @return 可安全持久化的错误摘要
     */
    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "未知发送异常";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * @author Virgor
     * @date 2026年10月07日
     * @description OutboxRow数据访问器，封装对应的业务职责。
     */
    private static class OutboxRowMapper implements RowMapper<OutboxEvent> {
        /**
         * 将当前结果集行映射为 Outbox 模型。
         *
         * @param resultSet 已定位到当前行的 message_outbox 查询结果集
         * @param rowNum Spring JDBC 当前行的序号，从 0 开始
         * @return 包含载荷、锁信息、版本及重试计数的 Outbox 快照
         * @throws SQLException 读取结果集失败时抛出
         */
        @Override
        public OutboxEvent mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            OutboxEvent event = new OutboxEvent();
            event.setId(resultSet.getLong("id"));
            event.setEventId(resultSet.getString("event_id"));
            event.setSourceService(resultSet.getString("source_service"));
            event.setAggregateType(resultSet.getString("aggregate_type"));
            event.setAggregateId(resultSet.getString("aggregate_id"));
            long aggregateVersion = resultSet.getLong("aggregate_version");
            event.setAggregateVersion(resultSet.wasNull() ? null : aggregateVersion);
            event.setEventType(resultSet.getString("event_type"));
            event.setEventVersion(resultSet.getInt("event_version"));
            event.setTopic(resultSet.getString("topic"));
            event.setTag(resultSet.getString("tag"));
            event.setMessageKey(resultSet.getString("message_key"));
            event.setPayload(resultSet.getString("payload"));
            event.setStatus(OutboxStatus.valueOf(resultSet.getString("status")));
            event.setRetryCount(resultSet.getInt("retry_count"));
            event.setNextRetryAt(toLocalDateTime(resultSet.getTimestamp("next_retry_at")));
            event.setLockedBy(resultSet.getString("locked_by"));
            event.setLockedAt(toLocalDateTime(resultSet.getTimestamp("locked_at")));
            return event;
        }

        /**
         * 将可空 JDBC 时间戳转换为 LocalDateTime。
         *
         * @param value JDBC 时间戳
         * @return 对应本地时间；输入为空时返回 null
         */
        private LocalDateTime toLocalDateTime(Timestamp value) {
            return value == null ? null : value.toLocalDateTime();
        }
    }
}
