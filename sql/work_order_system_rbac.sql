-- =============================================================
-- WorkOrderSystem RBAC 权限管理表（增量脚本）
-- 适用场景：已按 work_order_system_schema.sql 建好 15 张业务表的库
-- 新建库时无需单独执行本文件（work_order_system_schema.sql 已包含以下表）
-- =============================================================

SET NAMES utf8mb4;
USE `WorkOrderSystem`;

-- 角色表
CREATE TABLE IF NOT EXISTS `roles` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    `code`        VARCHAR(50)  NOT NULL                COMMENT '角色编码（如 ADMIN/HANDLER/USER）',
    `name`        VARCHAR(50)  NOT NULL                COMMENT '角色名称',
    `description` VARCHAR(255) NULL                    COMMENT '角色描述',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1=启用，0=禁用）',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_roles_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色表';

-- 权限表（菜单/按钮/接口，支持树形层级）
CREATE TABLE IF NOT EXISTS `permissions` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '权限主键',
    `code`        VARCHAR(100) NOT NULL                COMMENT '权限编码（如 ticket:assign）',
    `name`        VARCHAR(50)  NOT NULL                COMMENT '权限名称',
    `type`        TINYINT      NOT NULL DEFAULT 1      COMMENT '权限类型（1=菜单，2=按钮/操作，3=接口）',
    `parent_id`   BIGINT       NULL                    COMMENT '父级权限（菜单树），根为 NULL',
    `path`        VARCHAR(200) NULL                    COMMENT '资源路径（菜单路由或接口 URL）',
    `method`      VARCHAR(10)  NULL                    COMMENT '请求方法（接口类型使用，如 GET/POST）',
    `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1=启用，0=禁用）',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permissions_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `user_roles` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT   NOT NULL                COMMENT '用户 ID',
    `role_id`    BIGINT   NOT NULL                COMMENT '角色 ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色关联表';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS `role_permissions` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT   NOT NULL                COMMENT '角色 ID',
    `permission_id` BIGINT   NOT NULL                COMMENT '权限 ID',
    `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-权限关联表';

-- 外键约束
ALTER TABLE `permissions`
    ADD CONSTRAINT `fk_permissions_parent` FOREIGN KEY (`parent_id`) REFERENCES `permissions` (`id`);

ALTER TABLE `user_roles`
    ADD CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`);

ALTER TABLE `role_permissions`
    ADD CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
    ADD CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`);
