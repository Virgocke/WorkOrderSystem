package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.TicketResponse;
import com.WorkOrder.ticket.dto.CreateTicketDto;
import com.WorkOrder.ticket.enums.TicketStatus;
import com.WorkOrder.ticket.mapper.TicketCategoryMapper;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.model.Tickets;
import com.WorkOrder.ticket.service.TicketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Virgor
 * @date 2026年09月14日 04:08
 * @description 工单服务实现类
 */
@RequiredArgsConstructor
@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Tickets> implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketCategoryMapper categoryMapper;

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
     * 创建工单
     * @param creatorId 创建者ID
     * @param creatorName 创建者名称
     * @param createTicketDto 创建工单DTO
     * @return 工单响应对象
     */
    @Override
    public TicketResponse createTicket(Long creatorId, String creatorName, CreateTicketDto createTicketDto) {
        TicketResponse ticketResponse = new TicketResponse(); // 创建工单响应对象
        TicketCategory ticketCategory = categoryMapper.selectById(createTicketDto.getCategoryId()); // 根据类别ID获取类别信息
        LocalDateTime now = LocalDateTime.now(); // 获取当前时间

        if (createTicketDto.getPriority() == 0|| createTicketDto.getPriority() > 4){
            int defaultPriority = ticketCategory.getDefaultPriority();
            createTicketDto.setPriority(defaultPriority);
        }

        Tickets ticket = new Tickets();
        BeanUtils.copyProperties(createTicketDto, ticket);
        ticket.setCreatorId(creatorId); // 设置创建者ID

        //todo 生成工单编号，现在暂时使用随机数模拟
        String ticketNo = String.valueOf((int) (Math.random() * 10000000));
        ticket.setTicketNo(ticketNo); // 设置工单编号

        ticketResponse.setCategoryName(ticketCategory.getName()); // 设置工单类别名称
        ticketResponse.setCreatorName(creatorName);
        ticketResponse.setHandlerId(null); // 设置处理者ID为null
        ticketResponse.setHandlerName(null); // 设置处理者名称为null
        ticketResponse.setAssignedAt(null); // 设置分配时间为空

        ticket.setStatus(TicketStatus.PENDING_ASSIGN.name()); // 设置工单状态为待分配
        ticket.setResponseDeadline(
                now.plusMinutes(ticketCategory.getDefaultResponseSla())
        ); // 设置响应截止时间

        ticket.setResolutionDeadline(
                now.plusMinutes(ticketCategory.getDefaultResolutionSla())
        ); // 设置解决截止时间

        ticket.setFirstResponseAt(null); // 设置首次响应时间为null
        ticket.setResolvedAt(null); // 设置解决时间为null
        ticket.setClosedAt(null); // 设置关闭时间为null
        ticket.setSlaStatus("NORMAL"); // 设置SLA状态为正常
        ticket.setEscalatedLevel(0); // 设置升级级别为0
        ticket.setSource("WEB"); // 设置来源为WEB

        int insert = ticketMapper.insert(ticket);
        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.TICKET_CREATE_FAILED);
        }


        Tickets newTicket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Tickets>()
                        .eq(Tickets::getTicketNo, ticketNo));

        return buildTicketResponse(newTicket, ticketResponse);
    }

    private TicketResponse buildTicketResponse(Tickets ticket, TicketResponse ticketResponse) {
        // 复制工单属性到工单响应对象
        BeanUtils.copyProperties(
        ticket,
        ticketResponse,
        "responseDeadline",
        "resolutionDeadline",
        "firstResponseAt",
        "resolvedAt",
        "closedAt"
        );

        // 设置响应截止时间，解决截止时间，首次响应时间，解决时间和关闭时间
        ticketResponse.setResponseDeadline(
                formatTime(ticket.getResponseDeadline())
        );
        ticketResponse.setResolutionDeadline(
                formatTime(ticket.getResolutionDeadline())
        );
        ticketResponse.setFirstResponseAt(
                formatTime(ticket.getFirstResponseAt())
        );
        ticketResponse.setResolvedAt(
                formatTime(ticket.getResolvedAt())
        );
        ticketResponse.setClosedAt(
                formatTime(ticket.getClosedAt())
        );
        return ticketResponse;
    }
}
