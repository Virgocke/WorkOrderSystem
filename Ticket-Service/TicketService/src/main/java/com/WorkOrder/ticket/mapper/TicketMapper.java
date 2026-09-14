package com.WorkOrder.ticket.mapper;

import com.WorkOrder.ticket.dto.MonthlyCountDto;
import com.WorkOrder.ticket.dto.TicketHistoryStatisticsDto;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年09月14日 04:07
 * @description 工单Mapper接口
 */
@Mapper
public interface TicketMapper extends BaseMapper<Tickets> {

    /**
     * 查询当前用户的工单历史统计。
     *
     * @param userId 当前用户ID
     * @return 工单历史统计
     */
    TicketHistoryStatisticsDto selectHistoryStatistics(@Param("userId") Long userId);

    /**
     * 查询当前用户最近12个有工单记录月份的提交数量。
     *
     * @param userId 当前用户ID
     * @return 按月份升序排列的工单数量
     */
    List<MonthlyCountDto> selectMonthlyStatistics(@Param("userId") Long userId);
}
