package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.TicketRatingResponse;
import com.WorkOrder.model.user.UserProfile;
import com.WorkOrder.ticket.dto.TicketRatingDto;
import com.WorkOrder.ticket.enums.TicketStatusEnum;
import com.WorkOrder.ticket.feignclient.UserFeignClient;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.mapper.TicketRatingMapper;
import com.WorkOrder.ticket.model.TicketRating;
import com.WorkOrder.ticket.model.Tickets;
import com.WorkOrder.ticket.service.TicketRatingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * @author Virgor
 * @date 2026年09月15日 21:01
 * @description 工单评价服务实现类
 */
@Service
@RequiredArgsConstructor
public class TicketRatingServiceImpl implements TicketRatingService {

    private final TicketRatingMapper ticketRatingMapper;
    private final TicketMapper ticketMapper;
    private final UserFeignClient userFeignClient;


    /**
     * 获取工单评价
     * @param ticketId 工单id
     * @param userId 用户id
     * @return 工单评价响应对象
     */
    @Transactional(readOnly = true)
    @Override
    public TicketRatingResponse getTicketRating(Long ticketId, Long userId) {
        // 查询工单评价
        TicketRating ticketRating = ticketRatingMapper
                .selectOne(
                        new LambdaQueryWrapper<TicketRating>()
                                .eq(TicketRating::getTicketId, ticketId));
        // 如果没有评价则返回null
        if (ticketRating == null) {
            return null;
        }

        // 转换为工单评价响应对象
        TicketRatingResponse ticketRatingResponse = new TicketRatingResponse();
        BeanUtils.copyProperties(ticketRating, ticketRatingResponse);

        Tickets ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Tickets>()
                        .eq(Tickets::getId, ticketId)
        );
        // 设置工单编号
        ticketRatingResponse.setTicketNo(ticket.getTicketNo());

        // 设置用户ID和用户名
        ticketRatingResponse.setUserId(ticketRating.getUserId());
        Result<UserProfile> userProfile = userFeignClient.getById(ticketRating.getUserId());
        ticketRatingResponse.setUserName(userProfile.getData().getUsername());

        Result<UserProfile> result = userFeignClient.getById(ticketRating.getHandlerId());
        String handlerName = result.getData().getRealName();
        ticketRatingResponse.setHandlerName(handlerName);

        return ticketRatingResponse;
    }

    /**
     * 提交工单评价
     * @param ticketId 工单编号
     * @param ticketRatingDto 工单评价DTO
     * @return 是否成功
     */
    @Override
    @Transactional
    public Boolean ticketRatingSubmit(Long ticketId, Long userId, TicketRatingDto ticketRatingDto) {
        // 根据工单编号查询工单，如果不存在则抛出异常，表示工单不存在
        Tickets ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Tickets>()
                        .eq(Tickets::getId, ticketId)
        );
        if (ticket == null) {
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_FOUND);
        }
        if (!userId.equals(ticket.getCreatorId())) {
            throw new SystemException(SystemExceptionEnum.ACCESS_DENIED);
        }
        // 如果工单状态不是已解决或已关闭则抛出异常，表示工单不能评价
        if (
                !ticket.getStatus().equals(TicketStatusEnum.RESOLVED.name())
                        && !ticket.getStatus().equals(TicketStatusEnum.CLOSED.name())
        ){
            throw new SystemException(SystemExceptionEnum.TICKET_NOT_RESOLVED);
        }

        // 根据工单编号查询工单评价，如果存在则抛出异常，表示工单已评价
        TicketRating ticketRating = ticketRatingMapper
                .selectOne(
                        new LambdaQueryWrapper<TicketRating>()
                                .eq(TicketRating::getTicketId, ticketId)
                );
        if (ticketRating != null) {
            throw new SystemException(SystemExceptionEnum.TICKET_RATING_HAS_BEEN_MADE);
        }

        // 创建工单评价对象并设置属性
        ticketRating = new TicketRating();
        BeanUtils.copyProperties(ticketRatingDto, ticketRating);
        ticketRating.setTicketId(ticketId);
        ticketRating.setUserId(userId);
        if (ticket.getResolvedByHandlerId() == null) {
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        ticketRating.setHandlerId(ticket.getResolvedByHandlerId());
        // 插入数据库
        int insert = ticketRatingMapper.insert(ticketRating);
        if (insert < 1){
            throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
        }
        return true;
    }
}
