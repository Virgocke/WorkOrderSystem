-- 既有 WorkOrderSystem 数据库升级：增加事务性工单编号计数器。
-- 可重复执行。首次使用某个前缀和日期范围时，应用会从同范围已有工单编号中取最大数字尾号初始化。
SET NAMES utf8mb4;
USE `WorkOrderSystem`;

CREATE TABLE IF NOT EXISTS `ticket_number_counters` (
    `scope_key`  VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '编号前缀及日期部分',
    `last_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本范围已分配的最大序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`scope_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单编号计数器';
