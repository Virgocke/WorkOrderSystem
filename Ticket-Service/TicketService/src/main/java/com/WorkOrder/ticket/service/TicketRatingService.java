package com.WorkOrder.ticket.service;

import com.WorkOrder.model.ticket.TicketRatingResponse;

/**
 * @author Virgor
 * @date 2026年09月15日 20:59
 * @description 工单评价服务接口
 */
public interface TicketRatingService {

    TicketRatingResponse getTicketRating(Long ticketId, Long userId);
}
