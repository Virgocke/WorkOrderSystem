package com.WorkOrder.ticket.service.impl;

import com.WorkOrder.model.Result;
import com.WorkOrder.model.ticket.TicketRatingResponse;
import com.WorkOrder.model.user.UserProfile;
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

    @Transactional
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
                        .eq(Tickets::getTicketNo, ticketId)
        );
        // 设置工单编号
        ticketRatingResponse.setTicketNo(ticket.getTicketNo());

        // 设置用户ID和用户名
        ticketRatingResponse.setUserId(userId);
        Result<UserProfile> userProfile = userFeignClient.getById(userId);
        ticketRatingResponse.setUserName(userProfile.getData().getUsername());

        //todo 设置处理人名称，处理人模块还没完成
        ticketRatingResponse.setHandlerName(null);

        return ticketRatingResponse;
    }
}
