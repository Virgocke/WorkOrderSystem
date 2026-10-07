package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月15日 03:24
 * @description 持久化工单状态变更历史。
 */
@Mapper
public interface TicketStatusHistoryMapper extends BaseMapper<TicketStatusHistory> {
}
