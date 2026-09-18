package com.WorkOrder.notification.service;

import com.WorkOrder.model.notification.NotificationRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.MarkAsReadDto;
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

    /**
     * 获取当前用户的未读通知数。
     * @param receiverId 从认证信息读取的当前用户 ID
     * @return 未读通知数量
     */
    long unreadCount(Long receiverId);

    /**
     * 将通知标记为已读。
     * @param markAsReadDto 标记已读 DTO
     * @param receiverId 从认证信息读取的当前用户 ID
     * @return 已读通知数量
     */
    long markAsRead(MarkAsReadDto markAsReadDto, Long receiverId);

    /**
     * 将当前用户的全部通知标记为已读。
     * @param receiverId 从认证信息读取的当前用户 ID
     * @return 已读通知数量
     */
    long markAllAsRead(Long receiverId);
}
