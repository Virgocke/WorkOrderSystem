package com.WorkOrder.ticket.service;

import com.WorkOrder.model.ticket.TicketRatingResponse;
import com.WorkOrder.ticket.dto.TicketRatingDto;

import javax.validation.Valid;

/**
 * @author Virgor
 * @date 2026年09月15日 20:59
 * @description 工单评价服务接口
 */
public interface TicketRatingService {

    /**
     * 获取工单评价
     * @param ticketId 工单id
     * @param userId 用户id
     * @return 工单评价响应对象
     */
    TicketRatingResponse getTicketRating(Long ticketId, Long userId);

    /**
     * 提交工单评价
      * @param ticketId 工单id
     * @param ticketRatingDto 工单评价DTO
     * @return 是否提交成功
     */
    Boolean ticketRatingSubmit(Long ticketId, Long userId, @Valid TicketRatingDto ticketRatingDto);
}
