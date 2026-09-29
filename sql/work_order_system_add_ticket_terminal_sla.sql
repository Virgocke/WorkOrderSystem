-- 已有数据库在启用 CLOSED/CANCELLED 的 SLA 消费者前执行一次。
ALTER TABLE `sla_records`
    ADD COLUMN `terminal_status` VARCHAR(16) NULL COMMENT '工单终态（CLOSED/CANCELLED）',
    ADD COLUMN `terminal_at` DATETIME NULL COMMENT '首次进入终态时间';
