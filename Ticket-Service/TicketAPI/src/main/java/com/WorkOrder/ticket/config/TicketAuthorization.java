package com.WorkOrder.ticket.config;

import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.Tickets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author Virgor
 * @date 2026年09月15日 03:05
 * @description 工单权限控制类，用于判断当前用户是否有权限查看工单
 */
@Component("ticketAuthorization")
@RequiredArgsConstructor
public class TicketAuthorization {

    private final TicketMapper ticketMapper;
    private final CurrentUserIdProvider currentUserIdProvider;

    /**
     * 判断当前用户是否有权限查看工单
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @return 是否有权限查看工单
     */
    public boolean canView(Long ticketId, Authentication authentication) {
        Long currentUserId = currentUserIdProvider.get(authentication);
        Tickets ticket = ticketMapper.selectById(ticketId);

        if (ticket == null) {
            return false;
        }

        return Objects.equals(ticket.getCreatorId(), currentUserId)
                || Objects.equals(ticket.getHandlerId(), currentUserId);
    }
}
