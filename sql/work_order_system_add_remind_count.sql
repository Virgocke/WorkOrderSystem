-- 为现有工单表新增独立的催办次数；MySQL 8.0，可重复执行。
-- 已有工单默认从 0 次开始，保留原升级级别和 SLA 状态。
SET NAMES utf8mb4;
USE `WorkOrderSystem`;

SET @add_ticket_remind_count = IF(
    EXISTS (
        SELECT 1
        FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'tickets'
          AND `column_name` = 'remind_count'
    ),
    'SELECT 1',
    'ALTER TABLE `tickets` ADD COLUMN `remind_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''催办次数（每张工单最多 3 次，与升级级别独立）'' AFTER `escalated_level`'
);

PREPARE add_ticket_remind_count_stmt FROM @add_ticket_remind_count;
EXECUTE add_ticket_remind_count_stmt;
DEALLOCATE PREPARE add_ticket_remind_count_stmt;
