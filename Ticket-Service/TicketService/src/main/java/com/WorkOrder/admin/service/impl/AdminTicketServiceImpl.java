package com.WorkOrder.admin.service.impl;

import com.WorkOrder.admin.service.AdminTicketService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.handler.dto.AdminTicketListDto;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.converter.TicketConverter;
import com.WorkOrder.ticket.feignclient.UserFeignClient;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Virgor
 * @date 2026年09月17日 01:55
 * @description 管理员工单服务实现类
 */
@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {
    
    private final UserFeignClient userFeignClient;
    private TicketMapper ticketMapper;
    
    @Override
    public List<TicketResponse> getTicketListForAdmin(Long adminId, AdminTicketListDto adminTicketListDto) {
        Result<UserProfile> result = userFeignClient.getById(adminId);
        if (result == null || result.getData() == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }

        // 验证用户角色，确保是管理员
        UserProfile userProfile = result.getData();
        if (userProfile.getRole() != 2) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }

        LambdaQueryWrapper<Tickets> queryWrapper = new LambdaQueryWrapper<Tickets>();
        // 按状态查询
        if (adminTicketListDto.getStatus() != null){
            queryWrapper.eq(Tickets::getStatus, adminTicketListDto.getStatus());
        }
        // 按优先级查询
        if (adminTicketListDto.getPriority() != 0){
            queryWrapper.eq(Tickets::getPriority, adminTicketListDto.getPriority());
        }
        // 按类别查询
        if (adminTicketListDto.getCategoryId() != null){
            queryWrapper.eq(Tickets::getCategoryId, adminTicketListDto.getCategoryId());
        }
        // 按处理人查询
        if (adminTicketListDto.getHandlerId() != null){
            queryWrapper.eq(Tickets::getHandlerId, adminTicketListDto.getHandlerId());
        }
        // 按创建人查询
        if (adminTicketListDto.getCreatorId() != null){
            queryWrapper.eq(Tickets::getCreatorId, adminTicketListDto.getCreatorId());
        }
        // 按SLA状态查询
        if (adminTicketListDto.getSlaStatus() != null){
            queryWrapper.eq(Tickets::getSlaStatus, adminTicketListDto.getSlaStatus());
        }
        // 按时间范围查询
        if (adminTicketListDto.getStart() != null && adminTicketListDto.getEnd() != null){
            queryWrapper.between(Tickets::getCreatedAt, adminTicketListDto.getStart(), adminTicketListDto.getEnd());
        }
        // todo按关键字查询，等搜索模块完善之后用搜索模块代替
        if (adminTicketListDto.getKeyword() != null){
            queryWrapper.like(Tickets::getTitle, adminTicketListDto.getKeyword())
                    .or()
                    .like(Tickets::getDescription, adminTicketListDto.getKeyword());
        }
        queryWrapper.orderByDesc(Tickets::getCreatedAt);
        queryWrapper.orderByDesc(Tickets::getId);

        Page<Tickets> page = new Page<>(adminTicketListDto.getPage(), adminTicketListDto.getPageSize());

        Page<Tickets> tickets = ticketMapper.selectPage(page, queryWrapper);

        if (tickets.getRecords() == null || tickets.getRecords().isEmpty()) {
            return Collections.emptyList();
        }
        // 获取工单列表
        List<Tickets> records = tickets.getRecords();
        // 转换为响应对象
        List<TicketResponse> ticketResponses = records.stream().map(TicketConverter::toResponse).collect(Collectors.toList());

        return ticketResponses;
    }
}