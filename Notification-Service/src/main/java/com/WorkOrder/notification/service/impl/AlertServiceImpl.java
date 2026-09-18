package com.WorkOrder.notification.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;

import com.WorkOrder.model.notification.AlertRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.notification.dto.AlertDto;
import com.WorkOrder.notification.feignclient.TicketFeignClient;
import com.WorkOrder.notification.feignclient.UserFeignClient;
import com.WorkOrder.notification.mapper.AlertRecordMapper;
import com.WorkOrder.notification.model.AlertRecords;
import com.WorkOrder.notification.service.AlertService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年09月19日 00:24
 * @description 告警服务实现类
 */
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRecordMapper alertRecordMapper;
    private final UserFeignClient userFeignClient;
    private final TicketFeignClient ticketFeignClient;


    /**
     * 获取告警列表
     * @param operatorId 操作员ID
     * @param operatorRole 操作员角色
     * @param alertDto 告警查询条件
     * @return 告警列表
     */
    @Override
    public PageResult<AlertRecord> getAlertList(Long operatorId, String operatorRole, AlertDto alertDto) {

        // 当前角色来自后端认证信息，服务层再次校验管理员或处理权限。
        if (!"ADMIN".equals(operatorRole) && !"HANDLER".equals(operatorRole)) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        // 校验用户是否存在
        Result<UserProfile> result = userFeignClient.getById(operatorId);
        if (result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }
        if (result.getData().getStatus() != 1) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_DISABLED);
        }

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

        Map<Long, TicketResponse> ticketMap = new HashMap<>();
        Map<Long, UserProfile> userMap = new HashMap<>();

        List<AlertRecord> alertRecords = new ArrayList<>(records.size());
        for (AlertRecords record : records){
            AlertRecord alertRecord = new AlertRecord();
            BeanUtils.copyProperties(record, alertRecord);

            // 获取工单信息
            TicketResponse ticketResponse = ticketMap.computeIfAbsent(
                    record.getTicketId(), id -> ticketFeignClient.getTicketInfo(id).getData());
            alertRecord.setTicketNo(ticketResponse.getTicketNo());
            alertRecord.setTicketTitle(ticketResponse.getTitle());

            // 获取用户信息
            UserProfile userProfile = userMap.computeIfAbsent(
                    record.getTargetUserId(), id -> userFeignClient.getById(id).getData());
            alertRecord.setTargetName(userProfile.getRealName());

            alertRecord.setChannel(record.getNotificationChannel());
            alertRecords.add(alertRecord);
        }

        return new PageResult<>(alertRecords, page.getTotal(), alertDto.getPage(), alertDto.getPageSize());
    }
}
