package com.WorkOrder.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 通知渠道配置及收件邮箱查询，不缓存管理员配置。 */
@Mapper
public interface NotificationChannelMapper {
    /** 返回已保存的 JSON；缺失时由业务层采用默认配置。 */
    @Select("SELECT config_value FROM configurations WHERE config_key = 'notificationChannels'")
    String selectChannels();

    /** 返回启用账号的邮箱；在创建任务时保存地址快照，重试不重新选择接收人。 */
    @Select("SELECT email FROM users WHERE id = #{userId} AND status = 1")
    String selectEmail(@Param("userId") Long userId);
}
