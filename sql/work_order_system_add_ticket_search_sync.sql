-- 工单搜索同步阶段一升级脚本，适用于 MySQL 8.0（包含 8.0.11）。
-- 在 WorkOrderSystem 业务数据库中执行，可重复运行。
SET NAMES utf8mb4;

-- ADD COLUMN 的默认值直接为历史行提供版本 1，不执行 UPDATE，保留业务 updated_at。
-- 重跑仅检查列是否存在，不重置已增长的版本。
SET @ticket_search_sync_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tickets'
       AND COLUMN_NAME = 'source_version') = 0,
    'ALTER TABLE tickets ADD COLUMN source_version BIGINT NOT NULL DEFAULT 1 COMMENT ''工单已提交状态的源版本，由业务更新原子递增'' AFTER source',
    'DO 0'
);
PREPARE ticket_search_sync_statement FROM @ticket_search_sync_ddl;
EXECUTE ticket_search_sync_statement;
DEALLOCATE PREPARE ticket_search_sync_statement;

-- 固定主键 1 的控制记录用于锁定活动代次，第一阶段只准备持久状态。
CREATE TABLE IF NOT EXISTS `ticket_search_sync_state` (
    `id`                   TINYINT NOT NULL COMMENT '固定为 1 的共享控制记录',
    `current_job_id`       BIGINT NULL COMMENT '当前导入任务 ID',
    `write_generation`     BIGINT NULL COMMENT '当前写入代次',
    `write_alias`          VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前代次的专属写别名',
    `published_generation` BIGINT NULL COMMENT '最近发布到读别名的代次',
    `phase`                VARCHAR(20) NOT NULL DEFAULT 'NOT_READY' COMMENT 'NOT_READY/IMPORTING/VERIFYING/PUBLISHING/READY/FAILED',
    `last_error`           VARCHAR(1000) NULL COMMENT '最近同步失败原因',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单搜索同步共享控制状态';

-- 已存在时只保持原主键，保留阶段、代次、错误和业务时间。
INSERT INTO `ticket_search_sync_state` (`id`, `phase`) VALUES (1, 'NOT_READY')
ON DUPLICATE KEY UPDATE `id` = `id`;

CREATE TABLE IF NOT EXISTS `ticket_search_import_jobs` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入任务 ID',
    `request_id`     VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '管理请求幂等键',
    `reason`         VARCHAR(500) NOT NULL COMMENT '导入或重建原因',
    `generation`     BIGINT NOT NULL COMMENT '本任务的索引代次',
    `target_index`   VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本代次的物理索引',
    `write_alias`    VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变的本代次写别名',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'IMPORTING' COMMENT 'IMPORTING/VERIFYING/PUBLISHING/READY/FAILED',
    `scan_upper_id`  BIGINT NOT NULL DEFAULT 0 COMMENT '本轮扫描的有限主键上界',
    `last_ticket_id` BIGINT NOT NULL DEFAULT 0 COMMENT '整批成功后提交的扫描游标',
    `scanned_count`  BIGINT NOT NULL DEFAULT 0 COMMENT '本轮已扫描工单数',
    `covered_count`  BIGINT NOT NULL DEFAULT 0 COMMENT '已写入或被同版本及更高版本覆盖的工单数',
    `last_error`     VARCHAR(1000) NULL COMMENT '最近导入失败原因',
    `lease_owner`    VARCHAR(128) NULL COMMENT '当前扫描工作者标识',
    `lease_until`    DATETIME NULL COMMENT '扫描租约到期时间',
    `lease_token`    CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '本次领取令牌，防止旧工作者推进游标',
    `created_by`     BIGINT NULL COMMENT '发起任务的管理员用户 ID',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_search_import_request` (`request_id`),
    KEY `idx_ticket_search_import_lease` (`status`, `lease_until`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单搜索导入与重建任务';
