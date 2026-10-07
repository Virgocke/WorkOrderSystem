package com.WorkOrder.notification.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.notification.NotificationRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.MarkAsReadDto;
import com.WorkOrder.notification.dto.MyNotificationPageDto;
import com.WorkOrder.notification.dto.NotificationCountDto;
import com.WorkOrder.notification.service.NotificationService;
import com.WorkOrder.security.CurrentUserIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 通知接口控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserIdProvider currentUserIdProvider;

    /**
     * 获取我的通知列表，接收人由 Token 确定。
     *
     * @param authentication 当前用户认证信息
     * @param myNotificationPageDto 分页查询参数
     * @return 通知列表
     */
    @GetMapping
    public Result<PageResult<NotificationRecord>> myNotifications(Authentication authentication,
                                                                  MyNotificationPageDto myNotificationPageDto) {
        Long receiverId = currentUserIdProvider.get(authentication);
        return Result.success(notificationService.myNotifications(receiverId, myNotificationPageDto));
    }

    /**
     * 获取我的未读通知数，接收人由 Token 确定。
     *
     * @param authentication 当前用户认证信息
     * @return 未读通知数
     */
    @GetMapping("/unread-count")
    public Result<NotificationCountDto> unreadCount(Authentication authentication) {
        Long receiverId = currentUserIdProvider.get(authentication);
        return Result.success(new NotificationCountDto(notificationService.unreadCount(receiverId)));
    }

    /**
     * 将我的通知标记为已读，接收人由 Token 确定。
     *
     * @param markAsReadDto 标记已读参数
     * @param authentication 当前用户认证信息
     * @return 本次标记已读的通知数
     */
    @PostMapping("/read")
    public Result<NotificationCountDto> markAsRead(@Valid @RequestBody MarkAsReadDto markAsReadDto,
                                                  Authentication authentication) {
        Long receiverId = currentUserIdProvider.get(authentication);
        return Result.success(new NotificationCountDto(notificationService.markAsRead(markAsReadDto, receiverId)));
    }


    /**
     * 将当前用户的全部站内通知标记为已读。
     *
     * @param authentication 用于从 JWT 确定通知接收人的当前认证信息
     * @return 统一响应，包含本次更新的 INTERNAL 通知记录数
     */
    @PostMapping("/read-all")
    public Result<NotificationCountDto> markAllAsRead(Authentication authentication) {
        Long receiverId = currentUserIdProvider.get(authentication);
        return Result.success(new NotificationCountDto(notificationService.markAllAsRead(receiverId)));
    }
}
