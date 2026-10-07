package com.WorkOrder.notification.service;

import com.WorkOrder.model.notification.NotificationRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.MarkAsReadDto;
import com.WorkOrder.notification.dto.MyNotificationPageDto;
import com.WorkOrder.notification.model.Notifications;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 通知服务接口。
 */
public interface NotificationService extends IService<Notifications> {

    /**
     * 获取当前用户的通知列表。
     *
     * @param receiverId 从 JWT 读取的当前接收人用户 ID
     * @param myNotificationPageDto 页码、每页条数及 UNREAD 或 READ 筛选条件
     * @return 仅包含当前接收人 INTERNAL 通知及统一阅读状态的分页结果
     */
    PageResult<NotificationRecord> myNotifications(Long receiverId,
                                                  MyNotificationPageDto myNotificationPageDto);

    /**
     * 获取当前用户的未读通知数。
     *
     * @param receiverId 从 JWT 读取的当前接收人用户 ID
     * @return 当前接收人尚未标记 READ 的 INTERNAL 通知数量
     */
    long unreadCount(Long receiverId);

    /**
     * 将通知标记为已读。
     *
     * @param markAsReadDto 需要标记已读的站内通知 ID 集合
     * @param receiverId 从 JWT 读取的当前接收人用户 ID
     * @return 本次成功更新的通知条数；任何 ID 不属于当前接收人或不是 INTERNAL 时整笔操作失败
     */
    long markAsRead(MarkAsReadDto markAsReadDto, Long receiverId);

    /**
     * 将当前用户的全部通知标记为已读。
     *
     * @param receiverId 从 JWT 读取的当前接收人用户 ID
     * @return 本次更新的当前接收人 INTERNAL 通知条数
     */
    long markAllAsRead(Long receiverId);
}
