package com.WorkOrder.search.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 持久导入任务；租约和发布令牌只在管理接口中用于恢复，不作为业务查询契约。
 */
@Data
public class TicketSearchImportTask {
    /**
     * 任务主键，同时作为代次。
     */
    private Long id;
    /**
     * 创建请求幂等键。
     */
    private String requestId;
    /**
     * 管理员说明。
     */
    private String reason;
    /**
     * 不重复的代次。
     */
    private Long generation;
    /**
     * 服务端生成的物理索引。
     */
    private String targetIndex;
    /**
     * 本任务专属写别名。
     */
    private String writeAlias;
    /**
     * IMPORTING/VERIFYING/PUBLISHING/READY/FAILED。
     */
    private String status;
    /**
     * 本轮有限上界；不是提交水位。
     */
    private Long scanUpperId;
    /**
     * 已确认整批的末尾主键。
     */
    private Long lastTicketId;
    /**
     * 本轮扫描数量。
     */
    private Long scannedCount;
    /**
     * 本轮已写入或被新版覆盖的数量。
     */
    private Long coveredCount;
    /**
     * 最近故障分类。
     */
    private String lastError;
    /**
     * 当前扫描进程标识。
     */
    private String leaseOwner;
    /**
     * 主库时间计算的租约截止时间。
     */
    private LocalDateTime leaseUntil;
    /**
     * 每次领取的新令牌。
     */
    private String leaseToken;
    /**
     * 创建管理员。
     */
    private Long createdBy;
    /**
     * 目标创建、校验、激活完成时间。
     */
    private LocalDateTime targetInitializedAt;
    /**
     * 有限扫描上界已登记；区别于空表上界 0。
     */
    private LocalDateTime scanStartedAt;
    /**
     * 失败前的扫描阶段。
     */
    private String resumePhase;
    /**
     * 完整验证扫描完成时间。
     */
    private LocalDateTime verifiedAt;
    /**
     * 验证后的目标 refresh 完成时间。
     */
    private LocalDateTime refreshCompletedAt;
    /**
     * 累计失败批次。
     */
    private Long failureCount;
    /**
     * 最近失败的工单主键，可空。
     */
    private Long lastFailedTicketId;
    /**
     * 唯一发布操作令牌，不能依租约到期自动抢占。
     */
    private String publisherToken;
    /**
     * 发布进程标识。
     */
    private String publisherOwner;
    /**
     * 开始发布时间。
     */
    private LocalDateTime publishStartedAt;
    /**
     * 确认所有源实例部署和发布开关的管理员。
     */
    private Long deploymentConfirmedBy;
    /**
     * 部署确认时间。
     */
    private LocalDateTime deploymentConfirmedAt;
    /**
     * 源实例清单、版本及开关核对记录。
     */
    private String deploymentManifest;
    /**
     * 最近一次确认原发布者停止的管理员。
     */
    private Long publicationRecoveryBy;
    /**
     * 人工确认原发布进程停止的时间。
     */
    private LocalDateTime publicationRecoveryAt;
    /**
     * 停止确认依据，不含凭据或正文。
     */
    private String publicationRecoveryReason;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最后状态变化时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 返回只由已登记任务生成的不可变写入目标。
     *
     * @return 工单索引写入目标
     */
    public TicketSearchWriteTarget target() {
        return new TicketSearchWriteTarget(id, generation, targetIndex, writeAlias);
    }
}
