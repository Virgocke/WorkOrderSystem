package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.model.TicketOperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月15日 20:05
 * @description 工单操作日志数据库表操作日志Mapper
 */
@Mapper
public interface TicketOperationLogMapper extends BaseMapper<TicketOperationLog> {
}
