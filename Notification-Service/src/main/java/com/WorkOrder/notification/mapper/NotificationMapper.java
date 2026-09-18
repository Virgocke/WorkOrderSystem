package com.WorkOrder.notification.mapper;

import com.WorkOrder.notification.model.Notifications;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 通知记录 Mapper。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notifications> {

    /** 分页查询当前接收人的通知，关联工单编号。 */
    Page<Notifications> selectMyNotifications(Page<Notifications> page,
                                             @Param("receiverId") Long receiverId,
                                             @Param("status") String status);

    /** 统计当前接收人的未读通知数。 */
    long countUnreadNotifications(@Param("receiverId") Long receiverId);
}
