-- 为现有 WorkOrderSystem 数据库新增技能调整申请表；MySQL 8.0，可重复执行。
SET NAMES utf8mb4;
USE `WorkOrderSystem`;

CREATE TABLE IF NOT EXISTS `skill_applications` (
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
    KEY `idx_skill_applications_skill` (`skill_tag_id`),
    CONSTRAINT `fk_skill_applications_handler`
        FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_skill_applications_skill`
        FOREIGN KEY (`skill_tag_id`) REFERENCES `skill_tags` (`id`),
    CONSTRAINT `fk_skill_applications_reviewer`
        FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技能调整申请表';
