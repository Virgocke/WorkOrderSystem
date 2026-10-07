package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.model.Notifications;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 通知记录 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notifications> {

    /**
     * 分页查询当前接收人的通知，关联工单编号。
     *
     * @param page MyBatis-Plus 分页对象，包含页码和每页条数
     * @param receiverId 仅允许查询的通知接收人用户 ID
     * @param status READ 或 UNREAD 阅读状态筛选；为空时不限制阅读状态
     * @return 当前接收人 INTERNAL 通知及关联工单编号的分页结果
     */
    Page<Notifications> selectMyNotifications(Page<Notifications> page,
                                             @Param("receiverId") Long receiverId,
                                             @Param("status") String status);

    /**
     * 统计当前接收人的未读通知数。
     *
     * @param receiverId 需要统计站内未读数的接收人用户 ID
     * @return 当前接收人 INTERNAL 通知中状态不是 READ 的记录数
     */
    long countUnreadNotifications(@Param("receiverId") Long receiverId);
}
