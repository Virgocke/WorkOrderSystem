-- 阶段三升级脚本，先执行阶段一脚本；支持 MySQL 8.0.11，可重复运行。
-- 不重置已有任务、进度、代次、租约或完成事实。
SET NAMES utf8mb4;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='scan_started_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN scan_started_at DATETIME NULL COMMENT ''有限扫描上界已登记''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='target_initialized_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN target_initialized_at DATETIME NULL COMMENT ''受管目标已激活时间''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='resume_phase')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN resume_phase VARCHAR(20) NULL COMMENT ''故障前扫描阶段''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='verified_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN verified_at DATETIME NULL COMMENT ''完整验证完成时间''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='refresh_completed_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN refresh_completed_at DATETIME NULL COMMENT ''验证后刷新完成时间''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='failure_count')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN failure_count BIGINT NOT NULL DEFAULT 0 COMMENT ''累计失败批次''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='last_failed_ticket_id')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN last_failed_ticket_id BIGINT NULL COMMENT ''最近失败工单主键''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='publisher_token')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN publisher_token CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''唯一发布操作令牌''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='publisher_owner')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN publisher_owner VARCHAR(128) NULL COMMENT ''发布进程身份''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='publish_started_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN publish_started_at DATETIME NULL COMMENT ''发布时间''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='deployment_confirmed_by')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN deployment_confirmed_by BIGINT NULL COMMENT ''源实例部署确认管理员''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='deployment_confirmed_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN deployment_confirmed_at DATETIME NULL COMMENT ''部署确认时间''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='deployment_manifest')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN deployment_manifest VARCHAR(2000) NULL COMMENT ''源实例版本和发布开关确认记录''', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

CREATE TABLE IF NOT EXISTS ticket_search_reconciliation_state (
 id TINYINT NOT NULL,
 generation BIGINT NULL COMMENT '本轮固定代次',
 scan_upper_id BIGINT NULL COMMENT '本轮有限上界，空表示尚未开始',
 last_ticket_id BIGINT NOT NULL DEFAULT 0,
 lease_token CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME NULL,
 completed_rounds BIGINT NOT NULL DEFAULT 0,
 last_full_success_at DATETIME NULL,
 last_error VARCHAR(1000) NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单搜索独立周期对账状态';
INSERT INTO ticket_search_reconciliation_state(id) VALUES(1) ON DUPLICATE KEY UPDATE id=id;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='publication_recovery_by')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN publication_recovery_by BIGINT NULL', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='publication_recovery_at')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN publication_recovery_at DATETIME NULL', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;

SET @ticket_search_phase3_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='ticket_search_import_jobs' AND COLUMN_NAME='publication_recovery_reason')=0,
 'ALTER TABLE ticket_search_import_jobs ADD COLUMN publication_recovery_reason VARCHAR(500) NULL', 'DO 0');
PREPARE ticket_search_phase3_stmt FROM @ticket_search_phase3_ddl;
EXECUTE ticket_search_phase3_stmt;
DEALLOCATE PREPARE ticket_search_phase3_stmt;


