-- 将工单附件关系统一迁移到 attachments.ticket_id。
-- 执行前应确认 tickets.attachment_urls 中没有仍需保留的历史 URL 数据。
SET NAMES utf8mb4;
USE `WorkOrderSystem`;

SET @legacy_attachment_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tickets'
      AND COLUMN_NAME = 'attachment_urls'
);

SET @drop_legacy_attachment_column_sql = IF(
    @legacy_attachment_column_exists > 0,
    'ALTER TABLE `tickets` DROP COLUMN `attachment_urls`',
    'SELECT ''tickets.attachment_urls 已不存在，无需迁移'' AS message'
);

PREPARE drop_legacy_attachment_column_statement
    FROM @drop_legacy_attachment_column_sql;
EXECUTE drop_legacy_attachment_column_statement;
DEALLOCATE PREPARE drop_legacy_attachment_column_statement;
