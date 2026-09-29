package com.WorkOrder.ticket.mapper;

import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.dto.MonthlyCountDto;
import com.WorkOrder.ticket.dto.TicketHistoryStatisticsDto;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月14日 04:07
 * @description 工单Mapper接口
 */
@Mapper
public interface TicketMapper extends BaseMapper<Tickets> {

    /**
     * 只有待分配、没有处理人且目标处理人的档案负载低于容量时才确认系统派单。
     *
     * @param ticketId 工单 ID
     * @param handlerId 候选处理人用户 ID
     * @param assignedAt 实际确认派单时间
     * @return 成功确认时为 1；已人工分配或状态变化时为 0
     */
    @Update("UPDATE tickets SET handler_id = #{handlerId}, assigned_at = #{assignedAt}, "
            + "status = 'PENDING_RESPONSE' WHERE id = #{ticketId} "
            + "AND status = 'PENDING_ASSIGN' AND handler_id IS NULL "
            + "AND EXISTS (SELECT 1 FROM users u JOIN handler_profiles hp "
            + "ON hp.user_id = u.id WHERE u.id = #{handlerId} AND u.role = 1 "
            + "AND u.status = 1 AND hp.current_load < hp.max_capacity)")
    int assignIfPending(@Param("ticketId") long ticketId,
                        @Param("handlerId") long handlerId,
                        @Param("assignedAt") LocalDateTime assignedAt);

    /** 查询用于工单详情展示的用户名称，姓名为空时退回登录账号。 */
    @Select("SELECT COALESCE(NULLIF(real_name, ''), username) FROM users WHERE id = #{userId} LIMIT 1")
    String selectDisplayNameByUserId(@Param("userId") Long userId);

    /** 批量查询用户显示名所需的最小资料。 */
    List<UserProfile> selectDisplayNamesByUserIds(@Param("userIds") Collection<Long> userIds);

    /** 原子增加创建人的工单催办次数，仅在当前次数小于 3 时更新。 */
    int incrementRemindCount(@Param("ticketId") Long ticketId, @Param("userId") Long userId);

    /**
     * 查询当前启用的管理员 ID，作为催办通知接收人快照的一部分。
     *
     * @return 按用户 ID 升序排列的启用管理员 ID
     */
    @Select("SELECT id FROM users WHERE role = 2 AND status = 1 ORDER BY id")
    List<Long> selectActiveAdminIds();

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
