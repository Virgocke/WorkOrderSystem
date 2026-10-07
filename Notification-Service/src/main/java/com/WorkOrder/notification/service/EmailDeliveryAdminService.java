package com.WorkOrder.notification.service;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.notification.dto.EmailDeliveryAdminQuery;
import com.WorkOrder.notification.dto.EmailDeliveryAdminResponse;
import com.WorkOrder.notification.dto.EmailDeliveryRetryLogResponse;
import com.WorkOrder.notification.dto.EmailDeliveryRetryRequest;
import com.WorkOrder.notification.dto.EmailDeliveryRetryResponse;
import com.WorkOrder.notification.feignclient.UserFeignClient;
import feign.FeignException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理入口先校验当前账号，再委托独立 Spring Bean 执行数据库事务。
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class EmailDeliveryAdminService {
    private final UserFeignClient userFeignClient;
    private final EmailDeliveryAdminPageService pageService;
    private final EmailDeliveryRetryService retryService;

    /**
     * 保持用户服务调用与数据库读写事务分离，避免锁内跨服务查询。
     *
     * @param userFeignClient 查询管理员当前账号状态和角色的用户服务客户端
     * @param pageService 执行独立只读一致快照事务的邮件查询服务
     * @param retryService 执行任务、通知和审计原子重排事务的服务
     */
    public EmailDeliveryAdminService(UserFeignClient userFeignClient, EmailDeliveryAdminPageService pageService,
                                     EmailDeliveryRetryService retryService) {
        this.userFeignClient = userFeignClient;
        this.pageService = pageService;
        this.retryService = retryService;
    }

    /**
     * 管理员查看默认失败任务列表及同快照总数。
     *
     * @param operatorId 从 JWT 确定的当前管理员用户 ID，不能由客户端指定
     * @param query 待校验的投递状态、创建时间范围及分页条件
     * @return 邮件任务公开字段及总数的分页结果，默认仅 FAILED
     */
    public PageResult<EmailDeliveryAdminResponse> list(Long operatorId, EmailDeliveryAdminQuery query) {
        EmailDeliveryAdminQuery normalized = normalizeQuery(query);
        validateOperator(operatorId);
        return pageService.list(normalized);
    }

    /**
     * 管理员可确认原收件地址，不允许在此修改快照。
     *
     * @param operatorId 从 JWT 确定的当前管理员用户 ID，不能由客户端指定
     * @param deliveryId 邮件任务主键
     * @return 邮件任务公开详情及原收件地址，不含正文和工作者令牌
     */
    public EmailDeliveryAdminResponse detail(Long operatorId, Long deliveryId) {
        validateId(deliveryId);
        validateOperator(operatorId);
        return pageService.detail(deliveryId);
    }

    /**
     * 读取原任务的重发审计，操作人来自已验证 JWT。
     *
     * @param operatorId 从 JWT 确定的当前管理员用户 ID，不能由客户端指定
     * @param deliveryId 邮件任务主键
     * @param query 重发审计的页码和每页条数
     * @return 按接受轮次逆序的人工重发审计分页结果
     */
    public PageResult<EmailDeliveryRetryLogResponse> logs(Long operatorId, Long deliveryId,
                                                         EmailDeliveryAdminQuery query) {
        validateId(deliveryId);
        EmailDeliveryAdminQuery normalized = normalizeQuery(query);
        validateOperator(operatorId);
        return pageService.logs(deliveryId, normalized);
    }

    /**
     * HTTP 请求只重排原任务；账号启用状态检查发生在任务行锁之前。
     *
     * @param operatorId 从 JWT 确定的当前管理员用户 ID，不能由客户端指定
     * @param deliveryId 邮件任务主键
     * @param request 包含幂等请求 ID、预期轮次及重发原因的请求
     * @return 已接受轮次、当前状态及工作者启用状态；成功仅确认排队，重复请求返回原接受轮次
     */
    public EmailDeliveryRetryResponse retry(Long operatorId, Long deliveryId, EmailDeliveryRetryRequest request) {
        validateId(deliveryId);
        if (request == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        EmailDeliveryRetryRequest normalized = request.normalizedCopy();
        validateOperator(operatorId);
        return retryService.retry(deliveryId, operatorId, normalized);
    }

    /**
     * 按相邻告警写接口的规则查询账号当前启用状态，不接受客户端指定操作人。
     *
     * @param operatorId 从已认证 JWT 取得、需再次核对当前启用状态与管理员角色的用户 ID
     */
    private void validateOperator(Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }
        Result<UserProfile> result;
        try {
            result = userFeignClient.getById(operatorId);
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
            }
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        if (result == null) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        if (result.getCode() == SystemExceptionEnum.USER_NOT_FOUND.getCode()
                || result.getCode() == SystemExceptionEnum.RESOURCE_NOT_FOUND.getCode()) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (result.getCode() == SystemExceptionEnum.ACCOUNT_DISABLED.getCode()) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
        if (result.getCode() != SystemExceptionEnum.SUCCESS.getCode()) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
        if (result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(result.getData().getStatus())) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
        if (result.getData().getRole() != 2 || (result.getData().getId() != null
                && !Objects.equals(result.getData().getId(), operatorId))) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
    }

    /**
     * 对所有查询入口使用相同分页和创建时间校验。
     *
     * @param query 客户端输入的邮件查询条件，可为空
     * @return 只复制允许的输入字段并完成分页、状态和时间校验的新查询对象
     */
    private EmailDeliveryAdminQuery normalizeQuery(EmailDeliveryAdminQuery query) {
        return (query == null ? new EmailDeliveryAdminQuery() : query).normalizedCopy();
    }

    /**
     * 防止非法路径 ID 进入数据库。
     *
     * @param deliveryId 邮件投递任务 ID
     */
    private void validateId(Long deliveryId) {
        if (deliveryId == null || deliveryId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
    }
}
