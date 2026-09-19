package com.WorkOrder.dashboard.mapper;

import com.WorkOrder.dashboard.dto.DashboardGroupCountDto;
import com.WorkOrder.dashboard.dto.DashboardHandlerHeatRow;
import com.WorkOrder.dashboard.dto.DashboardSummaryDto;
import com.WorkOrder.dashboard.dto.DashboardTicketRow;
import com.WorkOrder.dashboard.dto.DashboardTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 管理端全局仪表盘统计查询。 */
@Mapper
public interface DashboardMapper {

    /**
     * 获取全局概览数据。
     *
     * @return 全局概览数据
     */
    DashboardSummaryDto selectSummary();

    /**
     * 获取工单状态分组计数。
     *
     * @return 工单状态分组计数
     */
    List<DashboardGroupCountDto> selectStatusCounts();

    /**
     * 获取工单优先级分组计数。
     *
     * @return 工单优先级分组计数
     */
    List<DashboardGroupCountDto> selectPriorityCounts();

    /**
     * 获取工单趋势数据。
     *
     * @return 工单趋势数据
     */
    List<DashboardTrendRow> selectTrend();

    /**
     * 获取工单处理人热力图数据。
     *
     * @param dates 需要统计的连续日期列表
     * @return 工单处理人热力图数据
     */
    List<DashboardHandlerHeatRow> selectHandlerHeat(@Param("dates") List<LocalDate> dates);

    /**
     * 获取最近工单数据。
     *
     * @return 最近工单数据
     */
    List<DashboardTicketRow> selectRecentTickets();
}
