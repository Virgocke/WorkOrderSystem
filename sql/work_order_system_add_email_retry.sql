-- 阶段四：管理员原邮件重试。先执行邮件任务基础迁移。
-- 部署时停止全部旧邮件 worker，迁移后升级全部 Notification-Service 实例再恢复。
-- 可重复执行；不重排 FAILED、不重置轮次/审计/已发送事实，不触碰收件人和正文快照。
SET NAMES utf8mb4;

SET @email_retry_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='notification_email_deliveries' AND COLUMN_NAME='retry_round')=0,
 'ALTER TABLE notification_email_deliveries ADD COLUMN retry_round INT NOT NULL DEFAULT 0 COMMENT ''管理员重试轮次，初次为0''', 'DO 0');
PREPARE email_retry_stmt FROM @email_retry_ddl;
EXECUTE email_retry_stmt;
DEALLOCATE PREPARE email_retry_stmt;

SET @email_retry_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='notification_email_deliveries' AND COLUMN_NAME='total_attempts')=0,
 'ALTER TABLE notification_email_deliveries ADD COLUMN total_attempts BIGINT NOT NULL DEFAULT 0 COMMENT ''跨轮累计领取次数''', 'DO 0');
PREPARE email_retry_stmt FROM @email_retry_ddl;
EXECUTE email_retry_stmt;
DEALLOCATE PREPARE email_retry_stmt;

-- 仅补足历史累计值；显式保留自动更新时间，重复执行也不会降低累计次数。
UPDATE notification_email_deliveries
SET total_attempts=GREATEST(total_attempts, attempts), updated_at=updated_at
WHERE total_attempts < attempts;

CREATE TABLE IF NOT EXISTS `notification_email_retry_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '邮件重试审计ID',
    `delivery_id` BIGINT NOT NULL COMMENT '原邮件任务ID',
    `notification_id` BIGINT NOT NULL COMMENT '原EMAIL通知ID',
    `request_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端幂等操作ID',
    `from_round` INT NOT NULL COMMENT '重试前轮次',
    `to_round` INT NOT NULL COMMENT '接受的新轮次',
    `previous_attempts` INT NOT NULL COMMENT '上一轮领取次数',
    `previous_total_attempts` BIGINT NOT NULL COMMENT '接受前累计领取次数',
    `previous_error` VARCHAR(255) NULL COMMENT '上一轮脱敏错误',
    `operator_id` BIGINT NOT NULL COMMENT '启用的管理员ID',
    `reason` VARCHAR(200) NOT NULL COMMENT '管理员填写的重试原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_retry_request` (`delivery_id`, `request_id`),
    UNIQUE KEY `uk_email_retry_round` (`delivery_id`, `to_round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员邮件重试审计';
