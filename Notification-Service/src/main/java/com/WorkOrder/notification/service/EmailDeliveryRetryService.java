package com.WorkOrder.notification.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.notification.dto.EmailDeliveryRetryRequest;
import com.WorkOrder.notification.dto.EmailDeliveryRetryResponse;
import com.WorkOrder.notification.mapper.EmailDeliveryAdminMapper;
import com.WorkOrder.notification.mapper.EmailDeliveryRetryLogMapper;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.EmailDelivery;
import com.WorkOrder.notification.model.EmailDeliveryRetryLog;
import com.WorkOrder.notification.model.Notifications;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 单条任务重排短事务；操作人账号启用校验由外层服务在锁前完成。
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class EmailDeliveryRetryService {
    private final EmailDeliveryAdminMapper emailMapper;
    private final EmailDeliveryRetryLogMapper retryLogMapper;
    private final NotificationMapper notificationMapper;
    private final boolean workerEnabled;

    /**
     * 注入同一数据源的任务、通知、审计入口及工作者配置。
     *
     * @param emailMapper 邮件任务管理查询、行锁及条件重排的数据访问器
     * @param retryLogMapper 成功人工重发审计及请求幂等记录的数据访问器
     * @param notificationMapper 与任务关联的 EMAIL 通知数据访问器
     * @param workerEnabled 与现有邮件工作者共用的 worker-enabled 配置，不是邮件渠道开关
     */
    public EmailDeliveryRetryService(EmailDeliveryAdminMapper emailMapper,
            EmailDeliveryRetryLogMapper retryLogMapper, NotificationMapper notificationMapper,
            @Value("${work-order.notification.email.worker-enabled:true}") boolean workerEnabled) {
        this.emailMapper = emailMapper;
        this.retryLogMapper = retryLogMapper;
        this.notificationMapper = notificationMapper;
        this.workerEnabled = workerEnabled;
    }

    /**
     * 复用旧任务和 EMAIL 通知；任何数据库步骤失败都回滚，不在请求中调用 SMTP。
     *
     * @param deliveryId 需要重新排队的原邮件任务主键
     * @param operatorId 已在外层核对启用状态及 ADMIN 角色的当前操作人用户 ID
     * @param request 包含 requestId、当前 expectedRetryRound 和规范化原因的人工重发请求
     * @return 接受轮次、读取时当前状态及工作者配置；幂等重放返回历史接受轮次，不再次重排
     */
    @Transactional(rollbackFor = Exception.class)
    public EmailDeliveryRetryResponse retry(Long deliveryId, Long operatorId, EmailDeliveryRetryRequest request) {
        if (deliveryId == null || deliveryId <= 0 || operatorId == null || operatorId <= 0 || request == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        EmailDeliveryRetryRequest normalized = request.normalizedCopy();
        EmailDelivery task = emailMapper.selectLocked(deliveryId);
        if (task == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }
        EmailDeliveryRetryLog previous = retryLogMapper.selectByRequest(deliveryId, normalized.getRequestId());
        if (previous != null) {
            validateReplay(previous, operatorId, normalized);
            return EmailDeliveryRetryResponse.accepted(deliveryId, previous.getToRound(), task.getStatus(), workerEnabled);
        }
        validateNewRound(task, normalized);
        Notifications notification = notificationMapper.selectById(task.getNotificationId());
        if (notification == null || !"EMAIL".equals(notification.getChannel())) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        if (emailMapper.requeue(deliveryId, normalized.getExpectedRetryRound(), now) != 1) {
            conflict();
        }
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notifications>()
                .eq(Notifications::getId, task.getNotificationId())
                .eq(Notifications::getChannel, "EMAIL")
                .set(Notifications::getStatus, "PENDING")
                .set(Notifications::getSentAt, null)
                .set(Notifications::getUpdatedAt, now));
        if (updated != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        EmailDeliveryRetryLog log = new EmailDeliveryRetryLog();
        log.setDeliveryId(task.getId());
        log.setNotificationId(task.getNotificationId());
        log.setRequestId(normalized.getRequestId());
        log.setFromRound(task.getRetryRound());
        log.setToRound(task.getRetryRound() + 1);
        log.setPreviousAttempts(task.getAttempts());
        log.setPreviousTotalAttempts(task.getTotalAttempts());
        log.setPreviousError(task.getLastError());
        log.setOperatorId(operatorId);
        log.setReason(normalized.getReason());
        log.setCreatedAt(now);
        if (retryLogMapper.insert(log) != 1) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        return EmailDeliveryRetryResponse.accepted(deliveryId, log.getToRound(), "PENDING", workerEnabled);
    }

    /**
     * 已成功的请求必须保持操作人、原轮次和规范化原因一致。
     *
     * @param previous 同一任务和 requestId 已提交成功的重发审计
     * @param operatorId 当前请求的已校验管理员用户 ID
     * @param request 本次规范化后的请求，用于核对原轮次与原因是否一致
     */
    private void validateReplay(EmailDeliveryRetryLog previous, Long operatorId, EmailDeliveryRetryRequest request) {
        if (!Objects.equals(previous.getOperatorId(), operatorId)
                || !Objects.equals(previous.getFromRound(), request.getExpectedRetryRound())
                || !Objects.equals(previous.getReason(), request.getReason())) {
            conflict();
        }
    }

    /**
     * 状态和预期轮次共同阻止迟到请求误开新轮次。
     *
     * @param task 已加行锁的当前邮件任务快照
     * @param request 待开新轮次的规范化请求，包含客户端预期当前轮次
     */
    private void validateNewRound(EmailDelivery task, EmailDeliveryRetryRequest request) {
        if (!"FAILED".equals(task.getStatus()) || !Objects.equals(task.getRetryRound(), request.getExpectedRetryRound())
                || task.getRetryRound() == null || task.getRetryRound() == Integer.MAX_VALUE) {
            conflict();
        }
        if (!EmailRecipientValidator.isValid(task.getRecipient())) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }

    /**
     * 统一使用 409 冲突提示刷新任务。
     */
    private void conflict() {
        throw new SystemException(SystemExceptionEnum.EMAIL_RETRY_CONFLICT);
    }
}
