-- 系统配置版本及修改记录；MySQL 8.0，可重复执行，保留已有配置值。
SET NAMES utf8mb4;
USE `WorkOrderSystem`;

SET @add_configuration_version = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'configurations' AND column_name = 'version'),
    'SELECT 1',
    'ALTER TABLE configurations ADD COLUMN version BIGINT NOT NULL DEFAULT 1 COMMENT ''配置版本，用于并发保存校验'''
);
PREPARE configuration_version_stmt FROM @add_configuration_version;
EXECUTE configuration_version_stmt;
DEALLOCATE PREPARE configuration_version_stmt;

SET @add_configuration_updated_by = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'configurations' AND column_name = 'updated_by'),
    'SELECT 1',
    'ALTER TABLE configurations ADD COLUMN updated_by BIGINT NULL COMMENT ''最后修改人用户ID'''
);
PREPARE configuration_updated_by_stmt FROM @add_configuration_updated_by;
EXECUTE configuration_updated_by_stmt;
DEALLOCATE PREPARE configuration_updated_by_stmt;

CREATE TABLE IF NOT EXISTS configuration_change_logs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    before_value TEXT NOT NULL COMMENT '修改前的JSON值，首次保存记录默认值',
    after_value TEXT NOT NULL COMMENT '修改后的JSON值',
    version BIGINT NOT NULL COMMENT '保存后的配置版本',
    operator_id BIGINT NOT NULL COMMENT '修改人用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_change_version (config_key, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置修改记录表';
