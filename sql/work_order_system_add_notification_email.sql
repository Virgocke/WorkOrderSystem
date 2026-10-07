-- 通知邮件任务迁移；先运行此脚本，再启动新版 Notification-Service。
-- 可重复执行，不修改已有通知和渠道开关。旧 sms 字段由读取逻辑兼容忽略。
CREATE TABLE IF NOT EXISTS `notification_email_deliveries` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '邮件任务ID',
    `notification_id` BIGINT NOT NULL COMMENT 'EMAIL通知记录ID',
    `recipient` VARCHAR(254) NULL COMMENT '收件邮箱快照；无有效邮箱时为空',
    `subject` VARCHAR(200) NOT NULL COMMENT '邮件主题快照',
    `content` TEXT NOT NULL COMMENT '纯文本正文快照',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/RETRY/SENT/FAILED',
    `attempts` INT NOT NULL DEFAULT 0 COMMENT '已领取次数',
    `next_attempt_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可领取时间',
    `lease_until` DATETIME NULL COMMENT '领取租约到期时间',
    `claim_token` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前领取令牌',
    `last_error` VARCHAR(255) NULL COMMENT '脱敏失败原因',
    `sent_at` DATETIME NULL COMMENT 'SMTP接受时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_notification` (`notification_id`),
    KEY `idx_email_ready` (`status`, `next_attempt_at`, `id`),
    KEY `idx_email_lease` (`status`, `lease_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知邮件投递队列';
