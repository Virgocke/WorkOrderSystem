package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.model.TicketRating;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Virgor
 * @date 2026年09月15日 20:59
 * @description 持久化工单评价并查询评价记录。
 */
@Mapper
public interface TicketRatingMapper extends BaseMapper<TicketRating> {
}
