-- 技能审核通知关联字段与审计查询索引；在业务数据库中执行，可重复运行。
SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification_records' AND COLUMN_NAME = 'skill_application_id') = 0, 'ALTER TABLE notification_records ADD COLUMN skill_application_id BIGINT NULL COMMENT ''技能申请ID，工单通知为空''', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_operation_logs' AND INDEX_NAME = 'idx_audit_created') = 0, 'ALTER TABLE ticket_operation_logs ADD INDEX idx_audit_created (created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_operation_logs' AND INDEX_NAME = 'idx_audit_operator_created') = 0, 'ALTER TABLE ticket_operation_logs ADD INDEX idx_audit_operator_created (operator_id, created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_status_history' AND INDEX_NAME = 'idx_audit_created') = 0, 'ALTER TABLE ticket_status_history ADD INDEX idx_audit_created (created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_status_history' AND INDEX_NAME = 'idx_audit_operator_created') = 0, 'ALTER TABLE ticket_status_history ADD INDEX idx_audit_operator_created (operator_id, created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuration_change_logs' AND INDEX_NAME = 'idx_audit_created') = 0, 'ALTER TABLE configuration_change_logs ADD INDEX idx_audit_created (created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuration_change_logs' AND INDEX_NAME = 'idx_audit_operator_created') = 0, 'ALTER TABLE configuration_change_logs ADD INDEX idx_audit_operator_created (operator_id, created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

SET @audit_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuration_change_logs' AND INDEX_NAME = 'idx_audit_config_created') = 0, 'ALTER TABLE configuration_change_logs ADD INDEX idx_audit_config_created (config_key, created_at, id)', 'SELECT 1');
PREPARE audit_statement FROM @audit_ddl;
EXECUTE audit_statement;
DEALLOCATE PREPARE audit_statement;

