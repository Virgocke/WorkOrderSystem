package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单附件元数据。
 *
 * 文件内容存储在 MinIO 中，本实体仅保存对象定位信息、业务归属和生命周期状态。
 */
@Data
@TableName("attachments")
public class Attachment {
    private Long id;
    private Long uploaderId;
    private String bucket;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long size;
    private String etag;
    private String status;
    private Long ticketId;
    private Long operationLogId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime boundAt;
    private LocalDateTime deletedAt;
}
