-- =============================================================
-- WorkOrderSystem 最小联调数据（幂等）
-- 依据：前端 docs/API接口文档.md 与 src/constants/index.ts
-- 演示账户：admin/Admin@123、handler/Handler@123、user/User@123
-- 密码均为 BCrypt 哈希，明文仅用于本地联调说明，禁止用于生产环境。
-- =============================================================

SET NAMES utf8mb4;
USE `WorkOrderSystem`;

START TRANSACTION;

-- 用户所属部门：仅在不存在时创建，重复执行不会新增记录。
INSERT INTO `departments` (`name`, `parent_id`)
SELECT '技术部', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `departments` WHERE `name` = '技术部' AND `parent_id` IS NULL
);

-- 三类演示账户；role 与接口契约一致：0=普通用户、1=处理人、2=管理员。
INSERT IGNORE INTO `users`
    (`username`, `password`, `real_name`, `email`, `phone`, `department_id`, `role`, `status`)
VALUES
    ('admin', '$2a$10$ft8Bk1VUooTkhToiXezc9.qqOpz7pxjurvyCGlw8nF1RJXKzIi1/y', '系统管理员',
     'admin@workorder.com', '13900000001',
     (SELECT `id` FROM `departments` WHERE `name` = '技术部' AND `parent_id` IS NULL ORDER BY `id` LIMIT 1), 2, 1),
    ('handler', '$2a$10$bvpbYmDFKl3KYS4jz/YjwOHbpKUV4T1K/thJbVxgB3Bp.DtnZwsL2', '李明',
     'liming@demo.com', '13800000001',
     (SELECT `id` FROM `departments` WHERE `name` = '技术部' AND `parent_id` IS NULL ORDER BY `id` LIMIT 1), 1, 1),
    ('user', '$2a$10$D4C7aP9eKxqxsM7Hs6ePnOEenN5QM927tUAA7//pkXBq5Np5nb/jq', '张小明',
     'zhangxm@demo.com', '13800000009', NULL, 0, 1);

UPDATE `departments` d
JOIN `users` u ON u.`username` = 'admin'
SET d.`manager_id` = u.`id`
WHERE d.`name` = '技术部' AND d.`parent_id` IS NULL AND d.`manager_id` IS NULL;

-- 一个可参与派单的处理人，以及一个技能标签和关联记录。
INSERT INTO `handler_profiles`
    (`user_id`, `max_capacity`, `current_load`, `avg_response_minutes`, `avg_resolution_minutes`, `sla_compliance_rate`, `rating_score`)
SELECT u.`id`, 10, 0, 20, 120, 98.00, 4.80
FROM `users` u
WHERE u.`username` = 'handler'
  AND NOT EXISTS (SELECT 1 FROM `handler_profiles` hp WHERE hp.`user_id` = u.`id`);

INSERT IGNORE INTO `skill_tags` (`name`, `description`)
VALUES ('网络', '网络设备、链路与连通性故障处理');

INSERT IGNORE INTO `handler_skills` (`handler_id`, `skill_tag_id`, `proficiency`)
SELECT hp.`id`, st.`id`, 5
FROM `handler_profiles` hp
JOIN `users` u ON u.`id` = hp.`user_id`
JOIN `skill_tags` st ON st.`name` = '网络'
WHERE u.`username` = 'handler';

-- 三个内置角色，对应 users.role 的枚举。
INSERT IGNORE INTO `roles` (`code`, `name`, `description`, `status`)
VALUES
    ('USER', '普通用户', '提交、跟踪和评价本人创建的工单', 1),
    ('HANDLER', '处理人', '处理、转派和升级已分配工单', 1),
    ('ADMIN', '管理员', '系统配置、人员和工单全局管理', 1);

-- 权限包含菜单、操作和接口三类；操作权限码与前端 DEFAULT_PERMISSIONS 完全一致。
INSERT IGNORE INTO `permissions` (`code`, `name`, `type`, `parent_id`, `path`, `method`, `sort_order`, `status`)
VALUES
    ('menu:work-order', '工单工作台', 1, NULL, '/tickets', NULL, 1, 1),
    ('ticket:create', '提交工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 10, 1),
    ('ticket:my', '我的工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 11, 1),
    ('ticket:detail', '查看工单详情', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 12, 1),
    ('ticket:reply', '回复工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 13, 1),
    ('ticket:remind', '催办工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 14, 1),
    ('ticket:cancel', '撤销工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 15, 1),
    ('ticket:confirm', '确认解决', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 16, 1),
    ('ticket:rate', '评价工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 17, 1),
    ('notification:view', '查看通知', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 18, 1),
    ('ticket:pool', '处理人工作池', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 20, 1),
    ('ticket:respond', '响应工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 21, 1),
    ('ticket:resolve', '提交解决方案', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 22, 1),
    ('ticket:transfer', '转派工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 23, 1),
    ('ticket:escalate', '升级工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 24, 1),
    ('ticket:note', '内部备注', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 25, 1),
    ('handler:skills', '维护个人技能', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 26, 1),
    ('alert:view', '查看告警', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 27, 1),
    ('ticket:manage', '工单管理', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 30, 1),
    ('ticket:assign', '分配工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 31, 1),
    ('ticket:close', '强制关闭工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 32, 1),
    ('ticket:batch', '批量处理工单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 33, 1),
    ('category:manage', '分类管理', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 34, 1),
    ('skill:manage', '技能管理', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 35, 1),
    ('user:manage', '用户管理', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 36, 1),
    ('department:manage', '部门管理', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 37, 1),
    ('handler:manage', '处理人管理', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 38, 1),
    ('sla:monitor', 'SLA 监控', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 39, 1),
    ('alert:handle', '处理告警', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 40, 1),
    ('report:view', '查看报表', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 41, 1),
    ('config:manage', '系统配置', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 42, 1),
    ('audit:view', '查看审计日志', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 43, 1),
    ('assign:engine', '智能派单', 2, (SELECT `id` FROM `permissions` p WHERE p.`code` = 'menu:work-order'), NULL, NULL, 44, 1),
    ('api:users:me', '获取当前用户资料', 3, NULL, '/api/users/me', 'GET', 100, 1);

-- 用户与角色一一对应。
INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `users` u
JOIN `roles` r ON r.`code` = CASE u.`role`
    WHEN 0 THEN 'USER' WHEN 1 THEN 'HANDLER' WHEN 2 THEN 'ADMIN' END
WHERE u.`username` IN ('admin', 'handler', 'user');

-- 将前端权限集合授予对应角色；所有角色都可读取自身资料接口。
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` IN (
    'menu:work-order', 'api:users:me',
    'ticket:create', 'ticket:my', 'ticket:detail', 'ticket:reply', 'ticket:remind',
    'ticket:cancel', 'ticket:confirm', 'ticket:rate', 'notification:view'
)
WHERE r.`code` = 'USER';

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` IN (
    'menu:work-order', 'api:users:me', 'ticket:pool', 'ticket:respond', 'ticket:resolve',
    'ticket:transfer', 'ticket:escalate', 'ticket:note', 'handler:skills', 'alert:view'
)
WHERE r.`code` = 'HANDLER';

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` IN (
    'menu:work-order', 'api:users:me', 'ticket:manage', 'ticket:assign', 'ticket:transfer',
    'ticket:close', 'ticket:batch', 'category:manage', 'skill:manage', 'user:manage',
    'department:manage', 'handler:manage', 'sla:monitor', 'alert:handle', 'report:view',
    'config:manage', 'audit:view', 'assign:engine', 'notification:view'
)
WHERE r.`code` = 'ADMIN';

COMMIT;
