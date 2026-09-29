-- RocketMQ 消息底座增量脚本（MySQL 8）

CREATE TABLE IF NOT EXISTS `message_outbox` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `event_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局唯一事件ID',
    `source_service` VARCHAR(64) NOT NULL COMMENT '生产服务',
    `aggregate_type` VARCHAR(64) NOT NULL COMMENT '聚合类型',
    `aggregate_id` VARCHAR(64) NOT NULL COMMENT '聚合ID',
    `aggregate_version` BIGINT NULL COMMENT '聚合版本',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
    `event_version` INT NOT NULL DEFAULT 1 COMMENT '事件结构版本',
    `topic` VARCHAR(128) NOT NULL COMMENT 'RocketMQ Topic',
    `tag` VARCHAR(64) NOT NULL COMMENT 'RocketMQ Tag',
    `message_key` VARCHAR(128) NOT NULL COMMENT 'RocketMQ Keys',
    `payload` JSON NOT NULL COMMENT '完整事件信封',
    `status` VARCHAR(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/SENDING/SENT/RETRY/DEAD',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已失败次数',
    `next_retry_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可发送时间',
    `locked_by` VARCHAR(128) NULL COMMENT '认领实例',
    `locked_at` DATETIME(3) NULL COMMENT '认领时间',
    `rocketmq_msg_id` VARCHAR(128) NULL COMMENT 'Broker消息ID',
    `last_error` VARCHAR(1000) NULL COMMENT '最后一次错误摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `sent_at` DATETIME(3) NULL COMMENT '成功发送时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_event_id` (`event_id`),
    KEY `idx_outbox_poll` (`source_service`, `status`, `next_retry_at`, `id`),
    KEY `idx_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`),
    KEY `idx_outbox_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务性消息发件箱';

CREATE TABLE IF NOT EXISTS `message_consume_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `consumer_group` VARCHAR(128) NOT NULL COMMENT '消费组',
    `event_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件ID',
    `topic` VARCHAR(128) NOT NULL COMMENT 'Topic',
    `tag` VARCHAR(64) NULL COMMENT 'Tag',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
    `consumed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务成功消费时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consumer_event` (`consumer_group`, `event_id`),
    KEY `idx_consume_event_id` (`event_id`),
    KEY `idx_consume_time` (`consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息消费幂等记录';

-- 通知业务幂等的第二道防线。历史通知允许 source_event_id 为空，
-- 新的消息消费者必须写入领域事件 ID。
SET @source_event_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification_records'
      AND COLUMN_NAME = 'source_event_id'
);
SET @add_source_event_column_sql = IF(
    @source_event_column_exists = 0,
    'ALTER TABLE `notification_records` ADD COLUMN `source_event_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''来源领域事件ID'' AFTER `receiver_id`',
    'SELECT 1'
);
PREPARE add_source_event_column_statement FROM @add_source_event_column_sql;
EXECUTE add_source_event_column_statement;
DEALLOCATE PREPARE add_source_event_column_statement;

SET @source_event_unique_key_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification_records'
      AND INDEX_NAME = 'uk_notification_source_receiver_channel'
);
SET @add_source_event_unique_key_sql = IF(
    @source_event_unique_key_exists = 0,
    'ALTER TABLE `notification_records` ADD UNIQUE KEY `uk_notification_source_receiver_channel` (`source_event_id`, `receiver_id`, `channel`)',
    'SELECT 1'
);
PREPARE add_source_event_unique_key_statement FROM @add_source_event_unique_key_sql;
EXECUTE add_source_event_unique_key_statement;
DEALLOCATE PREPARE add_source_event_unique_key_statement;
