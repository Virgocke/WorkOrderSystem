-- =============================================================
-- WorkOrderSystem 建库建表脚本
-- 数据库：MySQL 8.0，字符集 utf8mb4
-- 说明：文档中注明“所有表均应包含 created_at / updated_at”，
--       因此部分表格中省略的时间戳字段已按此补齐。
-- =============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `WorkOrderSystem`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `WorkOrderSystem`;

-- 已存在的旧库（若默认字符集不是 utf8mb4）统一调整，避免中文/特殊字符乱码
ALTER DATABASE `WorkOrderSystem`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- =============================================================
-- 1. 用户与组织
-- =============================================================

-- 1.1 部门表
CREATE TABLE `departments` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门主键',
    `name`       VARCHAR(100) NOT NULL                COMMENT '部门名称',
    `parent_id`  BIGINT       NULL                    COMMENT '上级部门，根部门为 NULL',
    `manager_id` BIGINT       NULL                    COMMENT '部门负责人',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门表';

-- 1.2 用户表
CREATE TABLE `users` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `username`      VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    `password`      VARCHAR(255) NOT NULL                COMMENT '加密密码',
    `real_name`     VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    `email`         VARCHAR(100) NULL                    COMMENT '邮箱',
    `phone`         VARCHAR(20)  NULL                    COMMENT '手机号',
    `department_id` BIGINT       NULL                    COMMENT '所属部门',
    `role`          TINYINT      NOT NULL DEFAULT 0      COMMENT '0=普通用户，1=处理人，2=管理员',
    `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '1=启用，0=禁用',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- 1.3 处理人信息表
CREATE TABLE `handler_profiles` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`                BIGINT       NOT NULL                COMMENT '关联用户',
    `max_capacity`           INT          NOT NULL DEFAULT 10     COMMENT '最大同时处理工单数',
    `current_load`           INT          NOT NULL DEFAULT 0      COMMENT '当前负载（冗余，实时值存 Redis）',
    `avg_response_minutes`   INT          DEFAULT 0               COMMENT '平均响应时长（分钟）',
    `avg_resolution_minutes` INT          DEFAULT 0               COMMENT '平均解决时长（分钟）',
    `sla_compliance_rate`    DECIMAL(5,2) DEFAULT 100.00          COMMENT 'SLA 达成率（百分比）',
    `rating_score`           DECIMAL(3,2) DEFAULT 5.00            COMMENT '平均用户评分（1-5）',
    `created_at`             DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '处理人信息表';

-- 1.4 技能标签表
CREATE TABLE `skill_tags` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '技能主键',
    `name`        VARCHAR(50)  NOT NULL                COMMENT '技能名称（如网络、数据库）',
    `description` VARCHAR(255) NULL                    COMMENT '技能描述',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_tags_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技能标签表';

-- 1.5 处理人-技能关联表
CREATE TABLE `handler_skills` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `handler_id`   BIGINT   NOT NULL                COMMENT '处理人 ID',
    `skill_tag_id` BIGINT   NOT NULL                COMMENT '技能 ID',
    `proficiency`  TINYINT  DEFAULT 1               COMMENT '熟练度（1-5）',
    `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_handler_skill` (`handler_id`, `skill_tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '处理人-技能关联表';

-- 1.6 技能调整申请表
CREATE TABLE `skill_applications` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请主键',
    `handler_id`         BIGINT       NOT NULL                COMMENT '申请人用户 ID（来自 Token）',
    `skill_tag_id`       BIGINT       NOT NULL                COMMENT '目标技能标签 ID',
    `proficiency`        TINYINT      NULL                    COMMENT '目标熟练度（1-5，REMOVE 时为 NULL）',
    `type`               VARCHAR(20)  NOT NULL                COMMENT '调整类型（ADD/ADJUST/REMOVE）',
    `reason`             VARCHAR(300) NULL                    COMMENT '申请理由',
    `status`             VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态（PENDING/APPROVED/REJECTED）',
    `reviewer_id`        BIGINT       NULL                    COMMENT '审核人用户 ID',
    `review_reason`      VARCHAR(300) NULL                    COMMENT '审核理由',
    `reviewed_at`        DATETIME     NULL                    COMMENT '审核时间',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_skill_applications_handler_status` (`handler_id`, `status`),
    KEY `idx_skill_applications_status_created` (`status`, `created_at`),
    KEY `idx_skill_applications_skill` (`skill_tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技能调整申请表';

-- =============================================================
-- 1.7 ~ 1.10 RBAC 权限管理
-- 说明：users.role（0/1/2）保留用于简单角色判断；
--       细粒度授权请使用以下 RBAC 表。
-- =============================================================

-- 1.7 角色表
CREATE TABLE `roles` (
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

-- 1.8 权限表（菜单/按钮/接口，支持树形层级）
CREATE TABLE `permissions` (
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

-- 1.9 用户-角色关联表
CREATE TABLE `user_roles` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT   NOT NULL                COMMENT '用户 ID',
    `role_id`    BIGINT   NOT NULL                COMMENT '角色 ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色关联表';

-- 1.10 角色-权限关联表
CREATE TABLE `role_permissions` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT   NOT NULL                COMMENT '角色 ID',
    `permission_id` BIGINT   NOT NULL                COMMENT '权限 ID',
    `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-权限关联表';

-- =============================================================
-- 2. 工单与分类
-- =============================================================

-- 2.1 工单分类表
CREATE TABLE `ticket_categories` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类主键',
    `name`                   VARCHAR(100) NOT NULL                COMMENT '分类名称',
    `parent_id`              BIGINT       NULL                    COMMENT '父分类，根为 NULL',
    `default_priority`       TINYINT      NOT NULL DEFAULT 2      COMMENT '默认优先级（1=紧急，2=高，3=中，4=低）',
    `default_response_sla`   INT          NULL DEFAULT NULL       COMMENT '响应时限（分钟），NULL使用系统默认值',
    `default_resolution_sla` INT          NULL DEFAULT NULL       COMMENT '解决时限（分钟），NULL使用系统默认值',
    `description`            VARCHAR(255) NULL                    COMMENT '分类描述',
    `created_at`             DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单分类表';

-- 分类所需技能；子分类未配置时沿用最近祖先分类的配置。
CREATE TABLE `ticket_category_skills` (
    `category_id`  BIGINT NOT NULL COMMENT '工单分类 ID',
    `skill_tag_id` BIGINT NOT NULL COMMENT '所需技能标签 ID',
    PRIMARY KEY (`category_id`, `skill_tag_id`),
    KEY `idx_category_skills_tag` (`skill_tag_id`),
    CONSTRAINT `fk_category_skills_category` FOREIGN KEY (`category_id`) REFERENCES `ticket_categories` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_category_skills_tag` FOREIGN KEY (`skill_tag_id`) REFERENCES `skill_tags` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分类所需技能';

-- 2.2 工单主表
CREATE TABLE `tickets` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单主键',
    `ticket_no`         VARCHAR(30)  NOT NULL                COMMENT '工单编号（规则生成）',
    `title`             VARCHAR(200) NOT NULL                COMMENT '工单标题',
    `description`       TEXT         NULL                    COMMENT '详细描述',
    `category_id`       BIGINT       NOT NULL                COMMENT '工单分类',
    `priority`          TINYINT      NOT NULL                COMMENT '优先级（1=紧急，2=高，3=中，4=低）',
    `status`            VARCHAR(30)  NOT NULL DEFAULT 'PENDING_ASSIGN' COMMENT '当前状态（状态机定义）',
    `creator_id`        BIGINT       NOT NULL                COMMENT '创建人（用户）',
    `handler_id`        BIGINT       NULL                    COMMENT '当前处理人（可能为空）',
    `resolved_by_handler_id` BIGINT NULL                    COMMENT '提交解决时的处理人快照，转派后归最终解决人',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `assigned_at`       DATETIME     NULL                    COMMENT '分配时间',
    `response_deadline` DATETIME     NOT NULL                COMMENT '响应截止时间（创建时计算）',
    `resolution_deadline` DATETIME   NOT NULL                COMMENT '解决截止时间（创建或分配时计算）',
    `first_response_at` DATETIME     NULL                    COMMENT '首次响应时间',
    `resolved_at`       DATETIME     NULL                    COMMENT '解决时间（处理人提交解决）',
    `closed_at`         DATETIME     NULL                    COMMENT '关闭时间（用户确认）',
    `sla_status`        VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'SLA 状态（NORMAL/NEAR_TIMEOUT/TIMEOUT/ESCALATED）',
    `escalated_level`   TINYINT      DEFAULT 0               COMMENT '升级级别（0=未升级，1=一级，2=二级...）',
    `remind_count`      INT UNSIGNED NOT NULL DEFAULT 0      COMMENT '催办次数（每张工单最多 3 次，与升级级别独立）',
    `source`            VARCHAR(20)  DEFAULT 'WEB'           COMMENT '来源（WEB、APP、API 等）',
    `source_version`    BIGINT       NOT NULL DEFAULT 1      COMMENT '工单已提交状态的源版本，由业务更新原子递增',
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tickets_ticket_no` (`ticket_no`),
    KEY `idx_status` (`status`),
    KEY `idx_handler_status` (`handler_id`, `status`),
    KEY `idx_tickets_resolved_handler` (`resolved_by_handler_id`, `resolved_at`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单主表';

-- 工单编号计数器；与工单创建在同一事务内更新，按前缀和格式化日期独立计数。
CREATE TABLE `ticket_number_counters` (
    `scope_key`  VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '编号前缀及日期部分',
    `last_value` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本范围已分配的最大序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`scope_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单编号计数器';

-- 工单搜索同步控制记录，固定主键 1 用于锁定活动索引代次。
CREATE TABLE `ticket_search_sync_state` (
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

INSERT INTO `ticket_search_sync_state` (`id`, `phase`) VALUES (1, 'NOT_READY');

CREATE TABLE `ticket_search_import_jobs` (
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

-- =============================================================
-- 3. 状态机与操作记录
-- =============================================================

-- 3.1 工单状态历史表
CREATE TABLE `ticket_status_history` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`   BIGINT       NOT NULL                COMMENT '工单 ID',
    `from_status` VARCHAR(30)  NULL                    COMMENT '原状态，创建工单时为 NULL',
    `to_status`   VARCHAR(30)  NOT NULL                COMMENT '目标状态',
    `event`       VARCHAR(50)  NOT NULL                COMMENT '触发事件（如 ASSIGN, SUBMIT_RESOLUTION）',
    `operator_id` BIGINT       NULL                    COMMENT '操作人（系统操作可为 NULL）',
    `remark`      VARCHAR(500) NULL                    COMMENT '备注',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_created` (`created_at`, `id`),
    KEY `idx_audit_operator_created` (`operator_id`, `created_at`, `id`),
    KEY `idx_ticket_id` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单状态历史表';

-- 3.2 工单操作日志表
CREATE TABLE `ticket_operation_logs` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`     BIGINT       NOT NULL                COMMENT '工单 ID',
    `action`        VARCHAR(50)  NOT NULL                COMMENT '操作类型（创建、分配、回复、关闭等）',
    `operator_id`   BIGINT       NULL                    COMMENT '操作人',
    `operator_role` VARCHAR(20)  NULL                    COMMENT '操作人角色（用户/处理人/系统）',
    `content`       TEXT         NULL                    COMMENT '操作内容或附加信息',
    `ip_address`    VARCHAR(50)  NULL                    COMMENT '操作 IP',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_created` (`created_at`, `id`),
    KEY `idx_audit_operator_created` (`operator_id`, `created_at`, `id`),
    KEY `idx_ticket_id` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单操作日志表';

-- 3.3 工单附件表
CREATE TABLE `attachments` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '附件主键',
    `uploader_id`      BIGINT        NOT NULL                COMMENT '上传人用户 ID',
    `bucket`           VARCHAR(63)   NOT NULL                COMMENT 'MinIO Bucket 名称',
    `object_key`       VARCHAR(512)  NOT NULL                COMMENT 'MinIO 对象键（应用生成，全局唯一）',
    `original_name`    VARCHAR(255)  NOT NULL                COMMENT '用户上传时的原始文件名',
    `content_type`     VARCHAR(255)  NOT NULL                COMMENT '校验后的媒体类型',
    `size`             BIGINT        NOT NULL                COMMENT '文件大小（字节）',
    `etag`             VARCHAR(128)  NULL                    COMMENT 'MinIO 对象 ETag',
    `status`           VARCHAR(20)   NOT NULL DEFAULT 'TEMP' COMMENT '状态（TEMP/READY/BOUND/DELETED/QUARANTINED）',
    `ticket_id`        BIGINT        NULL                    COMMENT '绑定的工单 ID，上传阶段可为空',
    `operation_log_id` BIGINT        NULL                    COMMENT '绑定的工单操作日志 ID，回复附件可使用',
    `expires_at`       DATETIME      NULL                    COMMENT '临时附件过期时间',
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `bound_at`         DATETIME      NULL                    COMMENT '绑定到业务记录的时间',
    `deleted_at`       DATETIME      NULL                    COMMENT '逻辑删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attachments_object_key` (`object_key`),
    KEY `idx_attachments_uploader_status` (`uploader_id`, `status`),
    KEY `idx_attachments_ticket` (`ticket_id`),
    KEY `idx_attachments_operation_log` (`operation_log_id`),
    KEY `idx_attachments_status_expires` (`status`, `expires_at`),
    CONSTRAINT `chk_attachments_status`
        CHECK (`status` IN ('TEMP', 'READY', 'BOUND', 'DELETED', 'QUARANTINED')),
    CONSTRAINT `chk_attachments_size`
        CHECK (`size` >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单附件元数据表';

-- =============================================================
-- 4. 分配与评分
-- =============================================================

-- 4.1 分配记录表
CREATE TABLE `assignment_records` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`          BIGINT       NOT NULL                COMMENT '工单 ID',
    `handler_id`         BIGINT       NOT NULL                COMMENT '被分配的处理人',
    `score`              DECIMAL(10,2) NOT NULL               COMMENT '分配时的综合评分',
    `assigned_by`        VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM' COMMENT '分配方式（SYSTEM/MANUAL）',
    `skill_match_score`  DECIMAL(5,2) NULL                    COMMENT '技能匹配得分（便于分析）',
    `load_score`         DECIMAL(5,2) NULL                    COMMENT '负载得分',
    `sla_score`          DECIMAL(5,2) NULL                    COMMENT 'SLA 表现得分',
    `rating_score`       DECIMAL(5,2) NULL                    COMMENT '历史评分得分',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_id` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分配记录表';

-- 4.2 用户评价表
CREATE TABLE `ticket_ratings` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`  BIGINT       NOT NULL                COMMENT '工单 ID',
    `user_id`    BIGINT       NOT NULL                COMMENT '评价用户',
    `handler_id` BIGINT       NULL                    COMMENT '评价归属的解决处理人快照',
    `rating`     TINYINT      NOT NULL                COMMENT '评分（1-5）',
    `comment`    VARCHAR(500) NULL                    COMMENT '评价内容',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_rating` (`ticket_id`),
    KEY `idx_ratings_handler` (`handler_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户评价表';

-- =============================================================
-- 5. SLA 与告警
-- =============================================================

-- 5.1 SLA 记录表
CREATE TABLE `sla_records` (
    `id`                       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`                BIGINT      NOT NULL                COMMENT '工单 ID',
    `response_deadline`        DATETIME    NOT NULL                COMMENT '响应截止时间',
    `resolution_deadline`      DATETIME    NOT NULL                COMMENT '解决截止时间',
    `first_response_at`        DATETIME    NULL                    COMMENT '首次响应时间（实际）',
    `resolved_at`              DATETIME    NULL                    COMMENT '实际解决时间',
    `response_duration_min`    INT         NULL                    COMMENT '响应时长（分钟）',
    `resolution_duration_min`  INT         NULL                    COMMENT '解决时长（分钟）',
    `is_response_timeout`      TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '是否响应超时（0/1）',
    `is_resolution_timeout`    TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '是否解决超时（0/1）',
    `current_escalation_level` TINYINT     DEFAULT 0               COMMENT '当前升级级别',
    `terminal_status`          VARCHAR(16) NULL                    COMMENT '工单终态（CLOSED/CANCELLED）',
    `terminal_at`              DATETIME    NULL                    COMMENT '首次进入终态时间',
    `created_at`               DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`               DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_sla` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SLA 记录表';

-- 5.2 告警记录表
CREATE TABLE `alert_records` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`            BIGINT       NOT NULL                COMMENT '工单 ID',
    `ticket_no_snapshot`   VARCHAR(30)  NULL                    COMMENT '告警发生时的工单编号',
    `ticket_title_snapshot` VARCHAR(200) NULL                   COMMENT '告警发生时的工单标题',
    `alert_type`           VARCHAR(20)  NOT NULL                COMMENT '告警类型（RESPONSE_TIMEOUT/RESOLUTION_TIMEOUT/ESCALATION）',
    `level`                TINYINT      NOT NULL                COMMENT '告警级别（1=提醒，2=警告，3=严重）',
    `message`              VARCHAR(500) NOT NULL                COMMENT '告警内容',
    `target_user_id`       BIGINT       NOT NULL                COMMENT '通知对象（处理人或主管）',
    `notification_channel` VARCHAR(20)  DEFAULT 'INTERNAL'      COMMENT '通知渠道（INTERNAL/EMAIL/SMS）',
    `status`               VARCHAR(20)  DEFAULT 'PENDING'       COMMENT '发送状态（PENDING/SENT/FAILED）',
    `sent_at`              DATETIME     NULL                    COMMENT '发送时间',
    `created_at`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_id` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '告警记录表';

-- =============================================================
-- 6. 通知与消息
-- =============================================================

-- 6.1 通知记录表
CREATE TABLE `notification_records` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_id`       BIGINT       NULL                    COMMENT '关联工单（可能为空，如系统通知）',
    `skill_application_id` BIGINT NULL COMMENT '关联技能申请',
    `receiver_id`     BIGINT       NOT NULL                COMMENT '接收人',
    `source_event_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '来源领域事件ID',
    `channel`         VARCHAR(20)  NOT NULL                COMMENT '渠道（INTERNAL/EMAIL）',
    `content`         TEXT         NOT NULL                COMMENT '通知内容',
    `status`          VARCHAR(20)  DEFAULT 'PENDING'       COMMENT '状态（PENDING/SENT/READ/FAILED）',
    `sent_at`         DATETIME     NULL                    COMMENT '发送时间',
    `read_at`         DATETIME     NULL                    COMMENT '阅读时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notification_source_receiver_channel` (`source_event_id`, `receiver_id`, `channel`),
    KEY `idx_receiver_id` (`receiver_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知记录表';

CREATE TABLE `notification_email_deliveries` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '邮件任务ID',
    `notification_id` BIGINT NOT NULL COMMENT 'EMAIL通知记录ID',
    `recipient` VARCHAR(254) NULL COMMENT '收件邮箱快照；无有效邮箱时为空',
    `subject` VARCHAR(200) NOT NULL COMMENT '邮件主题快照',
    `content` TEXT NOT NULL COMMENT '纯文本正文快照',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/RETRY/SENT/FAILED',
    `attempts` INT NOT NULL DEFAULT 0 COMMENT '已领取次数',
    `next_attempt_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可领取时间',
    `lease_until` DATETIME NULL COMMENT '领取租约到期时间',
    `claim_token` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前领取令牌',
    `last_error` VARCHAR(255) NULL COMMENT '脱敏失败原因',
    `sent_at` DATETIME NULL COMMENT 'SMTP接受时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_notification` (`notification_id`),
    KEY `idx_email_ready` (`status`, `next_attempt_at`, `id`),
    KEY `idx_email_lease` (`status`, `lease_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知邮件投递队列';

-- =============================================================
-- 7. 系统配置
-- =============================================================

-- 7.1 系统配置表
CREATE TABLE `configurations` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   VARCHAR(100) NOT NULL                COMMENT '配置键',
    `config_value` TEXT         NOT NULL                COMMENT '配置值（JSON 或字符串）',
    `description`  VARCHAR(255) NULL                    COMMENT '配置说明',
    `version`      BIGINT       NOT NULL DEFAULT 1      COMMENT '配置版本，用于并发保存校验',
    `updated_by`   BIGINT       NULL                    COMMENT '最后修改人用户ID',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_configurations_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置表';

-- 7.2 系统配置修改记录，与配置修改在同一事务内写入。
CREATE TABLE `configuration_change_logs` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   VARCHAR(100) NOT NULL COMMENT '配置键',
    `before_value` TEXT         NOT NULL COMMENT '修改前的JSON值，首次保存记录默认值',
    `after_value`  TEXT         NOT NULL COMMENT '修改后的JSON值',
    `version`      BIGINT       NOT NULL COMMENT '保存后的配置版本',
    `operator_id`  BIGINT       NOT NULL COMMENT '修改人用户ID',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    KEY `idx_audit_config_created` (`config_key`, `created_at`, `id`),
    PRIMARY KEY (`id`),
    KEY `idx_audit_created` (`created_at`, `id`),
    KEY `idx_audit_operator_created` (`operator_id`, `created_at`, `id`),
    UNIQUE KEY `uk_config_change_version` (`config_key`, `version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置修改记录表';

-- =============================================================
-- 外键约束（统一后置添加，解决 departments <-> users 等循环引用）
-- =============================================================

ALTER TABLE `departments`
    ADD CONSTRAINT `fk_departments_parent` FOREIGN KEY (`parent_id`) REFERENCES `departments` (`id`),
    ADD CONSTRAINT `fk_departments_manager` FOREIGN KEY (`manager_id`) REFERENCES `users` (`id`);

ALTER TABLE `users`
    ADD CONSTRAINT `fk_users_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`);

ALTER TABLE `handler_profiles`
    ADD CONSTRAINT `fk_handler_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `handler_skills`
    ADD CONSTRAINT `fk_handler_skills_handler` FOREIGN KEY (`handler_id`) REFERENCES `handler_profiles` (`id`),
    ADD CONSTRAINT `fk_handler_skills_skill` FOREIGN KEY (`skill_tag_id`) REFERENCES `skill_tags` (`id`);

ALTER TABLE `skill_applications`
    ADD CONSTRAINT `fk_skill_applications_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_skill_applications_skill` FOREIGN KEY (`skill_tag_id`) REFERENCES `skill_tags` (`id`),
    ADD CONSTRAINT `fk_skill_applications_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`);

ALTER TABLE `ticket_categories`
    ADD CONSTRAINT `fk_ticket_categories_parent` FOREIGN KEY (`parent_id`) REFERENCES `ticket_categories` (`id`);

ALTER TABLE `tickets`
    ADD CONSTRAINT `fk_tickets_category` FOREIGN KEY (`category_id`) REFERENCES `ticket_categories` (`id`),
    ADD CONSTRAINT `fk_tickets_creator` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_tickets_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_tickets_resolved_handler` FOREIGN KEY (`resolved_by_handler_id`) REFERENCES `users` (`id`);

ALTER TABLE `ticket_status_history`
    ADD CONSTRAINT `fk_status_history_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_status_history_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`);

ALTER TABLE `ticket_operation_logs`
    ADD CONSTRAINT `fk_operation_logs_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_operation_logs_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`);

ALTER TABLE `attachments`
    ADD CONSTRAINT `fk_attachments_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_attachments_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_attachments_operation_log` FOREIGN KEY (`operation_log_id`) REFERENCES `ticket_operation_logs` (`id`);

ALTER TABLE `assignment_records`
    ADD CONSTRAINT `fk_assignment_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_assignment_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`);

ALTER TABLE `ticket_ratings`
    ADD CONSTRAINT `fk_ratings_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_ratings_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_ratings_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`);

ALTER TABLE `sla_records`
    ADD CONSTRAINT `fk_sla_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`);

ALTER TABLE `alert_records`
    ADD CONSTRAINT `fk_alerts_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_alerts_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`);

ALTER TABLE `notification_records`
    ADD CONSTRAINT `fk_notifications_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    ADD CONSTRAINT `fk_notifications_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`);

-- RBAC 外键约束
ALTER TABLE `permissions`
    ADD CONSTRAINT `fk_permissions_parent` FOREIGN KEY (`parent_id`) REFERENCES `permissions` (`id`);

ALTER TABLE `user_roles`
    ADD CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    ADD CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`);

ALTER TABLE `role_permissions`
    ADD CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
    ADD CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`);
