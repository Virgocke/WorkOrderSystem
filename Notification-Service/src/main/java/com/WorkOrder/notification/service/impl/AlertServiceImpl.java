package com.WorkOrder.notification.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;

import com.WorkOrder.model.notification.AlertRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.notification.dto.AlertDto;
import com.WorkOrder.notification.feignclient.UserFeignClient;
import com.WorkOrder.notification.mapper.AlertRecordMapper;
import com.WorkOrder.notification.model.AlertRecords;
import com.WorkOrder.notification.service.AlertService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Virgor
 * @date 2026年09月19日 00:24
 * @description 告警服务实现类
 */
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    /**
     * 告警接口统一使用的日期时间格式。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 告警记录与工单摘要快照的数据访问组件。
     */
    private final AlertRecordMapper alertRecordMapper;
    /**
     * 操作人状态校验和告警接收人姓名查询。
     */
    private final UserFeignClient userFeignClient;


    /**
     * 获取告警列表
     *
     * @param operatorId 从 JWT 读取的当前操作人用户 ID
     * @param operatorRole 认证信息中的 ADMIN 或 HANDLER 角色快照
     * @param alertDto 告警类型、状态及分页筛选条件
     * @return 管理员可见全部告警、处理人仅可见本人告警的分页结果
     */
    @Override
    public PageResult<AlertRecord> getAlertList(Long operatorId, String operatorRole, AlertDto alertDto) {

        validateOperator(operatorId, operatorRole);

        Page<AlertRecords> alertRecordPage = new Page<>(alertDto.getPage(), alertDto.getPageSize());
        LambdaQueryWrapper<AlertRecords> queryWrapper = new LambdaQueryWrapper<AlertRecords>();

        // 按状态查询
        if (alertDto.getStatus() != null){
            queryWrapper.eq(AlertRecords::getStatus, alertDto.getStatus());
        }
        // 按类型查询
        if (alertDto.getType() != null){
            queryWrapper.eq(AlertRecords::getAlertType, alertDto.getType());
        }
        // 如果是处理角色，则只查询自己的告警
        if ("HANDLER".equals(operatorRole)){
            queryWrapper.eq(AlertRecords::getTargetUserId, operatorId);
        }
        queryWrapper.orderByDesc(AlertRecords::getCreatedAt);
        queryWrapper.orderByDesc(AlertRecords::getId);

        Page<AlertRecords> page = alertRecordMapper.selectPage(alertRecordPage, queryWrapper);
        List<AlertRecords> records = page.getRecords();

        Map<Long, UserProfile> userMap = new HashMap<>();

        List<AlertRecord> alertRecords = new ArrayList<>(records.size());
        for (AlertRecords record : records){
            alertRecords.add(toResponse(record, userMap));
        }

        return new PageResult<>(alertRecords, page.getTotal(), alertDto.getPage(), alertDto.getPageSize());
    }

    /**
     * 管理员可处理全部告警，处理人只能处理自己的告警。
     *
     * @param alertId 需要确认处理的告警主键
     * @param operatorId 从 JWT 读取的当前操作人用户 ID
     * @param operatorRole 认证信息中的 ADMIN 或 HANDLER 角色快照
     * @return 已标记 HANDLED 的告警响应；重复处理沿用已有状态和时间
     */
    @Override
    @Transactional
    public AlertRecord handleAlert(Long alertId, Long operatorId, String operatorRole) {
        if (alertId == null || alertId <= 0) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        validateOperator(operatorId, operatorRole);

        AlertRecords record = alertRecordMapper.selectById(alertId);
        validateAlertAccess(record, operatorId, operatorRole);

        // 已处理的告警直接返回，重复请求不改变更新时间。
        if (!"HANDLED".equals(record.getStatus())) {
            LocalDateTime updatedAt = LocalDateTime.now();
            int update = alertRecordMapper.update(null, new LambdaUpdateWrapper<AlertRecords>()
                    .eq(AlertRecords::getId, alertId)
                    .eq(!"ADMIN".equals(operatorRole), AlertRecords::getTargetUserId, operatorId)
                    .and(wrapper -> wrapper.ne(AlertRecords::getStatus, "HANDLED")
                            .or().isNull(AlertRecords::getStatus))
                    .set(AlertRecords::getStatus, "HANDLED")
                    .set(AlertRecords::getUpdatedAt, updatedAt));
            if (update != 1) {
                // 使用当前读确认并发处理结果，避免事务快照读到旧状态。
                record = alertRecordMapper.selectOne(new LambdaQueryWrapper<AlertRecords>()
                        .eq(AlertRecords::getId, alertId)
                        .last("FOR UPDATE"));
                validateAlertAccess(record, operatorId, operatorRole);
                if (!"HANDLED".equals(record.getStatus())) {
                    throw new SystemException(SystemExceptionEnum.ALERT_HANDLE_FAILED);
                }
            } else {
                record.setStatus("HANDLED");
                record.setUpdatedAt(updatedAt);
            }
        }

        return toResponse(record, new HashMap<>());
    }

    /**
     * 校验操作人的登录状态、角色及账号状态。
     *
     * @param operatorId 当前操作员的用户 ID
     * @param operatorRole 当前登录角色，由后端认证信息取得
     */
    private void validateOperator(Long operatorId, String operatorRole) {
        if (operatorId == null || operatorId <= 0) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }
        if (!"ADMIN".equals(operatorRole) && !"HANDLER".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        Result<UserProfile> result;
        try {
            result = userFeignClient.getById(operatorId);
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
            }
            throw exception;
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
        if (result.getData().getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }
    }

    /**
     * 校验告警是否存在，以及当前处理人是否为告警目标用户。
     *
     * @param record 待确认处理的告警记录，可为空
     * @param operatorId 当前操作人用户 ID
     * @param operatorRole 决定可处理范围的 ADMIN 或 HANDLER 认证角色
     */
    private void validateAlertAccess(AlertRecords record, Long operatorId, String operatorRole) {
        if (record == null) {
            throw new SystemException(SystemExceptionEnum.RESOURCE_NOT_FOUND);
        }
        if (!"ADMIN".equals(operatorRole)
                && !Objects.equals(operatorId, record.getTargetUserId())) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
    }

    /**
     * 将告警记录转换为接口响应，工单摘要直接读取持久化快照。
     * 工单转派或改名不影响历史摘要；快照缺失时按接口契约返回空值。
     *
     * @param record 已通过查询范围或访问权限校验的告警记录
     * @param userMap 本次请求内的接收人信息缓存
     * @return 包含工单摘要、接收人姓名和格式化时间的告警响应
     */
    private AlertRecord toResponse(AlertRecords record, Map<Long, UserProfile> userMap) {
        AlertRecord alertRecord = new AlertRecord();
        BeanUtils.copyProperties(record, alertRecord);
        alertRecord.setChannel(record.getNotificationChannel());
        alertRecord.setSentAt(record.getSentAt() == null ? null
                : record.getSentAt().format(DATE_TIME_FORMATTER));
        alertRecord.setCreatedAt(record.getCreatedAt() == null ? null
                : record.getCreatedAt().format(DATE_TIME_FORMATTER));

        alertRecord.setTicketNo(record.getTicketNoSnapshot());
        alertRecord.setTicketTitle(record.getTicketTitleSnapshot());

        UserProfile userProfile = userMap.computeIfAbsent(record.getTargetUserId(), this::getTargetProfile);
        if (userProfile != null) {
            alertRecord.setTargetName(userProfile.getRealName());
        }
        return alertRecord;
    }

    /**
     * 目标用户已删除时，历史告警的目标姓名保留为空。
     *
     * @param targetUserId 告警原接收人的用户 ID
     * @return 接收人当前公开资料；用户已删除或 ID 为空时为 null
     */
    private UserProfile getTargetProfile(Long targetUserId) {
        try {
            Result<UserProfile> result = userFeignClient.getById(targetUserId);
            if (result == null) {
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            }
            if (result.getCode() == SystemExceptionEnum.USER_NOT_FOUND.getCode()
                    || result.getCode() == SystemExceptionEnum.RESOURCE_NOT_FOUND.getCode()) {
                return null;
            }
            if (result.getCode() != SystemExceptionEnum.SUCCESS.getCode()) {
                throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
            }
            return result.getData();
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                return null;
            }
            throw exception;
        }
    }
}
