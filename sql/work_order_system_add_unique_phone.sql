-- =============================================================
-- 为已存在的 WorkOrderSystem.users 表补充手机号唯一索引
-- MySQL 8.0；可重复执行
-- 注意：若已有重复的非 NULL 手机号，ALTER TABLE 会失败，请先清理重复数据。
-- =============================================================

USE `WorkOrderSystem`;

SET @add_users_phone_unique_index = IF(
    EXISTS (
        SELECT 1
        FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'users'
          AND `index_name` = 'uk_users_phone'
    ),
    'SELECT 1',
    'ALTER TABLE `users` ADD UNIQUE KEY `uk_users_phone` (`phone`)'
);

PREPARE add_users_phone_unique_index_stmt FROM @add_users_phone_unique_index;
EXECUTE add_users_phone_unique_index_stmt;
DEALLOCATE PREPARE add_users_phone_unique_index_stmt;
