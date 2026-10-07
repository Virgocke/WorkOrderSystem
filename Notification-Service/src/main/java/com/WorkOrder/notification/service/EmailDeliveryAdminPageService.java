package com.WorkOrder.notification.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.EmailDeliveryAdminQuery;
import com.WorkOrder.notification.dto.EmailDeliveryAdminResponse;
import com.WorkOrder.notification.dto.EmailDeliveryRetryLogResponse;
import com.WorkOrder.notification.mapper.EmailDeliveryAdminMapper;
import com.WorkOrder.notification.mapper.EmailDeliveryRetryLogMapper;
import com.WorkOrder.notification.model.EmailDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理查询使用短只读快照；用户服务鉴权在外层完成。
 */
@Service
public class EmailDeliveryAdminPageService {
    private final EmailDeliveryAdminMapper emailMapper;
    private final EmailDeliveryRetryLogMapper retryLogMapper;
    private final boolean workerEnabled;

    /**
     * 注入查询 Mapper 和与邮件工作者相同的启用配置。
     *
     * @param emailMapper 邮件任务管理查询、行锁及条件重排的数据访问器
     * @param retryLogMapper 成功人工重发审计及请求幂等记录的数据访问器
     * @param workerEnabled 与现有邮件工作者共用的 worker-enabled 配置，不是邮件渠道开关
     */
    public EmailDeliveryAdminPageService(EmailDeliveryAdminMapper emailMapper,
            EmailDeliveryRetryLogMapper retryLogMapper,
            @Value("${work-order.notification.email.worker-enabled:true}") boolean workerEnabled) {
        this.emailMapper = emailMapper;
        this.retryLogMapper = retryLogMapper;
        this.workerEnabled = workerEnabled;
    }

    /**
     * 列表和总数来自同一快照，避免发送并发推进时出现分页口径不一致。
     *
     * @param query 待校验的投递状态、创建时间范围及分页条件
     * @return 邮件任务公开字段及总数的分页结果，默认仅 FAILED
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<EmailDeliveryAdminResponse> list(EmailDeliveryAdminQuery query) {
        List<EmailDeliveryAdminResponse> list = emailMapper.selectPage(query).stream()
                .map(task -> EmailDeliveryAdminResponse.from(task, false, workerEnabled))
                .collect(Collectors.toList());
        return new PageResult<>(list, emailMapper.countPage(query), query.getPage(), query.getPageSize());
    }

    /**
     * 详情只额外公开原收件地址，不返回正文或工作者令牌。
     *
     * @param deliveryId 邮件任务主键
     * @return 邮件任务公开详情及原收件地址，不含正文和工作者令牌
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EmailDeliveryAdminResponse detail(Long deliveryId) {
        return EmailDeliveryAdminResponse.from(requireTask(deliveryId), true, workerEnabled);
    }

    /**
     * 对存在任务返回重发审计，列表和总数在同一快照读取。
     *
     * @param deliveryId 邮件任务主键
     * @param query 重发审计的页码和每页条数
     * @return 按接受轮次逆序的人工重发审计分页结果
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<EmailDeliveryRetryLogResponse> logs(Long deliveryId, EmailDeliveryAdminQuery query) {
        requireTask(deliveryId);
        List<EmailDeliveryRetryLogResponse> list = retryLogMapper
                .selectRetryLogs(deliveryId, query.getPageSize(), query.getOffset()).stream()
                .map(EmailDeliveryRetryLogResponse::from).collect(Collectors.toList());
        return new PageResult<>(list, retryLogMapper.countByDelivery(deliveryId), query.getPage(), query.getPageSize());
    }

    /**
     * 邮件任务不存在时使用公共 404 业务错误。
     *
     * @param deliveryId 必须存在的邮件任务主键
     * @return 邮件任务的公开字段；任务不存在时抛出资源不存在错误
     */
    private EmailDelivery requireTask(Long deliveryId) {
        EmailDelivery task = emailMapper.selectDetail(deliveryId);
        if (task == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }
        return task;
    }
}
