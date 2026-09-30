package com.WorkOrder.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 工单创建与自动升级时读取系统 SLA 配置。 */
@Mapper
public interface TicketSlaConfigurationMapper {
    /** 锁定读获取最新已提交规则，避免消费事务旧快照；缺失表示关闭自动升级。 */
    @Select("SELECT config_value FROM configurations WHERE config_key = 'escalationRules' FOR UPDATE")
    String selectEscalationRules();
    /** @return SLA 默认值 JSON；配置记录不存在时返回 null */
    @Select("SELECT config_value FROM configurations WHERE config_key = 'slaDefaults'")
    String selectDefaultsJson();
}
