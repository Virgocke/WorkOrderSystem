package com.WorkOrder.notification.service;

import com.WorkOrder.model.notification.NotificationRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.MyNotificationPageDto;
import com.WorkOrder.notification.model.Notifications;
import com.baomidou.mybatisplus.extension.service.IService;

/** 通知服务接口。 */
public interface NotificationService extends IService<Notifications> {

    /**
     * 获取当前用户的通知列表。
     * @param receiverId 从认证信息读取的当前用户 ID
     * @param myNotificationPageDto 分页及阅读状态
     * @return 通知分页结果
     */
    PageResult<NotificationRecord> myNotifications(Long receiverId,
                                                  MyNotificationPageDto myNotificationPageDto);
}
