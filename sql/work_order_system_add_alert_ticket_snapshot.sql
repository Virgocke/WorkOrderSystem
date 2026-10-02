-- 告警工单摘要快照升级脚本，适用于 MySQL 8.0（包含本机 8.0.11）。
-- 在 WorkOrderSystem 业务数据库中执行；使用列存在检查，可重复运行。
-- 新告警从升级事件保存快照，查询告警时不再调用工单详情接口。
SET NAMES utf8mb4;

-- 新增可空字段，允许兼容未带标题的旧事件和无法补录的历史数据。
SET @alert_snapshot_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_records'
       AND COLUMN_NAME = 'ticket_no_snapshot') = 0,
    'ALTER TABLE alert_records ADD COLUMN ticket_no_snapshot VARCHAR(30) NULL COMMENT ''告警发生时的工单编号'' AFTER ticket_id',
    'DO 0'
);
PREPARE alert_snapshot_statement FROM @alert_snapshot_ddl;
EXECUTE alert_snapshot_statement;
DEALLOCATE PREPARE alert_snapshot_statement;

SET @alert_snapshot_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_records'
       AND COLUMN_NAME = 'ticket_title_snapshot') = 0,
    'ALTER TABLE alert_records ADD COLUMN ticket_title_snapshot VARCHAR(200) NULL COMMENT ''告警发生时的工单标题'' AFTER ticket_no_snapshot',
    'DO 0'
);
PREPARE alert_snapshot_statement FROM @alert_snapshot_ddl;
EXECUTE alert_snapshot_statement;
DEALLOCATE PREPARE alert_snapshot_statement;

-- 旧告警只能补录工单的当前摘要，无法还原告警发生时的历史标题。
-- 仅填充 NULL 字段，不覆盖已经保存的快照；关联工单缺失时保留空值。
-- 显式保持 updated_at 原值，避免 ON UPDATE CURRENT_TIMESTAMP 改变告警更新时间。
UPDATE alert_records AS alert
JOIN tickets AS ticket ON ticket.id = alert.ticket_id
SET alert.ticket_no_snapshot = COALESCE(alert.ticket_no_snapshot, ticket.ticket_no),
    alert.ticket_title_snapshot = COALESCE(alert.ticket_title_snapshot, ticket.title),
    alert.updated_at = alert.updated_at
WHERE alert.ticket_no_snapshot IS NULL OR alert.ticket_title_snapshot IS NULL;

SELECT ROW_COUNT() AS backfilled_alert_count;
