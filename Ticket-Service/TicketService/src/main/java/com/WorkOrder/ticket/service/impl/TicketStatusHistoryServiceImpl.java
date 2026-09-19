package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.model.ticket.StatusHistory;
import com.WorkOrder.ticket.mapper.TicketStatusHistoryMapper;
import com.WorkOrder.ticket.model.TicketStatusHistory;
import com.WorkOrder.ticket.service.TicketStatusHistoryService;
import com.WorkOrder.ticket.support.TicketActorNameResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月15日 03:25
 * @description
 */
@Service
@RequiredArgsConstructor
public class TicketStatusHistoryServiceImpl implements TicketStatusHistoryService {

    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketActorNameResolver actorNameResolver;

    // 时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 格式化时间
     * @param time 时间
     * @return 格式化后的时间
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }


    /**
     * 获取工单状态时间线
     * @param ticketId 工单ID
      * @return 工单状态时间线
     */
    @Override
    public List<StatusHistory> getTicketStatusTimeline(Long ticketId) {
        List<TicketStatusHistory> histories = ticketStatusHistoryMapper.selectList(
                new LambdaQueryWrapper<TicketStatusHistory>()
                        .eq(TicketStatusHistory::getTicketId, ticketId)
                        .orderByAsc(TicketStatusHistory::getCreatedAt)
                        .orderByAsc(TicketStatusHistory::getId)
        );
        Map<Long, String> operatorNames = actorNameResolver.resolveNames(
                histories.stream()
                        .map(TicketStatusHistory::getOperatorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
        );
        return histories.stream()
            .map(history -> convertToStatusHistory(history, operatorNames))
            .collect(Collectors.toList());
    }

    /**
     * 将TicketStatusHistory转换为StatusHistory
     * @param history 数据库里工单状态历史记录
     * @return 转换后，用于返回前端的状态历史记录
     */
    private StatusHistory convertToStatusHistory(TicketStatusHistory history,
                                                 Map<Long, String> operatorNames) {
        StatusHistory result = new StatusHistory();

        result.setId(history.getId());
        result.setTicketId(history.getTicketId());
        result.setFromStatus(history.getFromStatus());
        result.setToStatus(history.getToStatus());
        result.setEvent(history.getEvent());
        result.setOperatorId(history.getOperatorId());
        result.setOperatorName(operatorNames.get(history.getOperatorId()));
        result.setRemark(history.getRemark());
        result.setCreatedAt(formatTime(history.getCreatedAt()));

        return result;
    }
}
