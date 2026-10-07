-- 保留所有分类现有时限；管理员明确清空后才改为继承系统默认值。
-- 不修改历史工单截止时间。可重复执行。
SET NAMES utf8mb4;
USE WorkOrderSystem;
ALTER TABLE ticket_categories
    MODIFY COLUMN default_response_sla INT NULL DEFAULT NULL COMMENT '响应时限（分钟），NULL使用系统默认值',
    MODIFY COLUMN default_resolution_sla INT NULL DEFAULT NULL COMMENT '解决时限（分钟），NULL使用系统默认值';
