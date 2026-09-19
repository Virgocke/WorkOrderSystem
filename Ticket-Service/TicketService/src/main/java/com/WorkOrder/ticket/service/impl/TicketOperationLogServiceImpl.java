package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.model.ticket.OperationLog;
import com.WorkOrder.ticket.mapper.TicketOperationLogMapper;
import com.WorkOrder.ticket.model.TicketOperationLog;
import com.WorkOrder.ticket.service.TicketOperationLogService;
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
 * @date 2026年09月15日 20:06
 * @description 工单操作日志服务实现类
 */
@Service
@RequiredArgsConstructor
public class TicketOperationLogServiceImpl implements TicketOperationLogService {

    private final TicketOperationLogMapper ticketOperationLogMapper;
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
     * 根据工单ID获取工单操作日志
     * @param ticketId 工单ID
      * @return 工单操作日志列表
     */
    @Override
    public List<OperationLog> getTicketOperationLog(Long ticketId) {

        // 根据工单ID查询工单操作日志
        List<TicketOperationLog> ticketOperationLogList = ticketOperationLogMapper
                .selectList(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
                        .orderByAsc(TicketOperationLog::getCreatedAt)
                        .orderByAsc(TicketOperationLog::getId)
        );



        Map<Long, String> operatorNames = actorNameResolver.resolveNames(
                ticketOperationLogList.stream()
                        .map(TicketOperationLog::getOperatorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
        );

        List<OperationLog> operationLogList = ticketOperationLogList.stream()
                .map(log -> convertToOperationLog(log, operatorNames))
                .collect(Collectors.toList());

        return operationLogList;
    }

    /**
     * 将TicketOperationLog转换为OperationLog
     * @param log 工单操作日志
     * @return 转换后的工单操作日志
     */
    private OperationLog convertToOperationLog(TicketOperationLog log,
                                               Map<Long, String> operatorNames) {
        OperationLog result = new OperationLog();

        result.setId(log.getId());
        result.setTicketId(log.getTicketId());
        result.setAction(log.getAction());
        result.setOperatorId(log.getOperatorId());
        result.setOperatorName(operatorNames.get(log.getOperatorId()));
        result.setOperatorRole(log.getOperatorRole());
        result.setContent(log.getContent());
        result.setCreatedAt(formatTime(log.getCreatedAt()));

        // 附件表接入后再查询并填充。
        result.setAttachments(new String[0]);

        return result;
    }
}
