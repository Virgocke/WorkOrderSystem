package com.WorkOrder.notification.service;


import com.WorkOrder.model.notification.AlertRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.AlertDto;

/**
 * @author Virgor
 * @date 2026年09月19日 00:24
 * @description 告警服务
 */
public interface AlertService {
    /**
     * 获取告警列表
     *
     * @param operatorId 从 JWT 读取的当前操作人用户 ID
     * @param operatorRole 认证信息中的 ADMIN 或 HANDLER 角色快照
     * @param alertDto 告警类型、状态及分页筛选条件
     * @return 管理员可见全部告警、处理人仅可见本人告警的分页结果
     */
    PageResult<AlertRecord> getAlertList(Long operatorId, String operatorRole, AlertDto alertDto);

    /**
     * 处理告警。
     *
     * @param alertId 需要确认处理的告警主键
     * @param operatorId 从 JWT 读取的当前操作人用户 ID
     * @param operatorRole 认证信息中的 ADMIN 或 HANDLER 角色快照
     * @return 已标记 HANDLED 的告警响应；重复处理沿用已有状态和时间
     */
    AlertRecord handleAlert(Long alertId, Long operatorId, String operatorRole);
}
