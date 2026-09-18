package com.WorkOrder.notification.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.notification.NotificationRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.MarkAsReadDto;
import com.WorkOrder.notification.dto.MyNotificationPageDto;
import com.WorkOrder.notification.mapper.NotificationMapper;
import com.WorkOrder.notification.model.Notifications;
import com.WorkOrder.notification.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** 我的通知列表服务实现。 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notifications>
        implements NotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationMapper notificationMapper;

    /**
     * 获取当前用户的通知列表。
     * @param receiverId 从认证信息读取的当前用户 ID
     * @param myNotificationPageDto 分页及阅读状态
     * @return 当前用户通知列表
     */
    @Override
    public PageResult<NotificationRecord> myNotifications(Long receiverId,
                                                         MyNotificationPageDto myNotificationPageDto) {
        if (receiverId == null || receiverId <= 0) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }
        if (myNotificationPageDto == null || myNotificationPageDto.getPage() == null
                || myNotificationPageDto.getPageSize() == null
                || myNotificationPageDto.getPage() < 1 || myNotificationPageDto.getPageSize() < 1) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        long page = myNotificationPageDto.getPage();
        long pageSize = myNotificationPageDto.getPageSize();

        // 防止溢出
        try {
            Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException exception) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        // 验证状态参数
        String status = myNotificationPageDto.getStatus();
        if (status != null && !status.isEmpty() && !"UNREAD".equals(status) && !"READ".equals(status)) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }

        Page<Notifications> notificationPage = notificationMapper.selectMyNotifications(
                new Page<>(page, pageSize), receiverId, status);
        List<NotificationRecord> records = notificationPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageResult<>(records, notificationPage.getTotal(), page, pageSize);
    }

    /**
     * 获取当前用户的未读通知数，统计规则与通知列表一致。
     * @param receiverId 从认证信息读取的当前用户 ID
     * @return 未读通知数量
     */
    @Override
    public long unreadCount(Long receiverId) {
        if (receiverId == null || receiverId <= 0) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }
        return notificationMapper.countUnreadNotifications(receiverId);
    }

    /**
     * 标记通知为已读。
     * @param markAsReadDto 标记已读 DTO
     * @param receiverId 从认证信息读取的当前用户 ID
     * @return 已读通知数量
     */
    @Override
    @Transactional
    public long markAsRead(MarkAsReadDto markAsReadDto, Long receiverId) {
        List<Long> ids = markAsReadDto.getIds();
        int count = 0;
        for (Long id : ids) {
            LambdaUpdateWrapper<Notifications> queryWrapper = new LambdaUpdateWrapper<Notifications>();
            queryWrapper.eq(Notifications::getId, id);
            queryWrapper.eq(Notifications::getReceiverId, receiverId);
            queryWrapper.set(Notifications::getStatus, "READ");
            queryWrapper.set(Notifications::getReadAt, LocalDateTime.now());
            int update = notificationMapper.update(null, queryWrapper);
            if (update < 1) {
                throw new SystemException(SystemExceptionEnum.NOTIFICATION_READ_FAILED);
            }
            count++;
        }
        return count;
    }

    /**
     * 标记所有通知为已读。
     * @param receiverId 从认证信息读取的当前用户 ID
     * @return 已读通知数量
     */
    @Override
    public long markAllAsRead(Long receiverId) {
        int update = notificationMapper.update(null, new LambdaUpdateWrapper<Notifications>()
                .eq(Notifications::getReceiverId, receiverId)
                .set(Notifications::getStatus, "READ")
                .set(Notifications::getReadAt, LocalDateTime.now()));
        return update;
    }

    /**
     * 保留空工单字段，统一阅读状态及时间格式。
     * @param notification 通知记录
     * @return 通知记录
     */
    private NotificationRecord toResponse(Notifications notification) {
        return NotificationRecord.builder()
                .id(notification.getId())
                .ticketId(notification.getTicketId())
                .ticketNo(notification.getTicketNo())
                .receiverId(notification.getReceiverId())
                .channel(notification.getChannel())
                .content(notification.getContent())
                .status("READ".equals(notification.getStatus()) ? "READ" : "UNREAD")
                .createdAt(notification.getCreatedAt() == null ? null
                        : notification.getCreatedAt().format(DATE_TIME_FORMATTER))
                .build();
    }
}
