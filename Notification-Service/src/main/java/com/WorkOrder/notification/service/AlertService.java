package com.WorkOrder.notification.service;


import com.WorkOrder.model.notification.AlertRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.AlertDto;

/**
 * @author Virgor
 * @date 2026年09月19日 00:24
 * @description
 */
public interface AlertService {
    /**
     * 获取告警列表
     * @param operatorId 操作员ID
     * @param operatorRole 操作员角色
     * @param alertDto 告警查询条件
     * @return 告警列表
     */
    PageResult<AlertRecord> getAlertList(Long operatorId, String operatorRole, AlertDto alertDto);
}
