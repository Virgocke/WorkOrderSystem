package com.WorkOrder.notification.controller;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.notification.NotificationRecord;
import com.WorkOrder.model.page.PageResult;
import com.WorkOrder.notification.dto.MyNotificationPageDto;
import com.WorkOrder.notification.dto.NotificationCountDto;
import com.WorkOrder.notification.service.NotificationService;
import com.WorkOrder.security.CurrentUserIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通知接口控制器。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserIdProvider currentUserIdProvider;

    /**
     * 获取我的通知列表，接收人由 Token 确定。
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
     * @param authentication 当前用户认证信息
     * @return 未读通知数
     */
    @GetMapping("/unread-count")
    public Result<NotificationCountDto> unreadCount(Authentication authentication) {
        Long receiverId = currentUserIdProvider.get(authentication);
        return Result.success(new NotificationCountDto(notificationService.unreadCount(receiverId)));
    }

    //todo 10.3 按通知 ID 标记本人通知已读。
    //todo 10.4 将当前用户的全部通知标记为已读。
}
