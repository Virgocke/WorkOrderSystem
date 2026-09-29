-- 为现有 WorkOrderSystem 数据库新增工单附件元数据表；MySQL 8.0，可重复执行。
SET NAMES utf8mb4;
USE `WorkOrderSystem`;

CREATE TABLE IF NOT EXISTS `attachments` (
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
        CHECK (`size` >= 0),
    CONSTRAINT `fk_attachments_uploader`
        FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_attachments_ticket`
        FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
    CONSTRAINT `fk_attachments_operation_log`
        FOREIGN KEY (`operation_log_id`) REFERENCES `ticket_operation_logs` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单附件元数据表';
