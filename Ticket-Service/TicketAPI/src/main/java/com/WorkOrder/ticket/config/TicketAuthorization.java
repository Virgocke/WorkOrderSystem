package com.WorkOrder.ticket.config;

import com.WorkOrder.file.mapper.AttachmentMapper;
import com.WorkOrder.file.service.AttachmentService;
import com.WorkOrder.security.CurrentUserIdProvider;
import com.WorkOrder.ticket.mapper.TicketMapper;
import com.WorkOrder.ticket.model.Attachment;
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
    private final AttachmentMapper attachmentMapper;

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

    /**
     * 判断当前用户是否是工单创建者
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @return 是否是工单创建者
     */
    public boolean isCreator(Long ticketId, Authentication authentication){
        Long currentUserId = currentUserIdProvider.get(authentication);
        Tickets ticket = ticketMapper.selectById(ticketId);

        if (ticket == null) {
            return false;
        }

        return Objects.equals(ticket.getCreatorId(), currentUserId);
    }

    /**
     * 判断当前用户是否是工单处理者
     * @param ticketId 工单ID
     * @param authentication 当前用户认证信息
     * @return 是否是工单处理者
     */
    public boolean isHandler(Long ticketId, Authentication authentication){
        Long currentUserId = currentUserIdProvider.get(authentication);
        Tickets ticket = ticketMapper.selectById(ticketId);

        if (ticket == null) {
            return false;
        }

        return Objects.equals(ticket.getHandlerId(), currentUserId);
    }

    /**
     * 判断当前用户是否是附件上传者
     * @param attachmentId 附件ID
     * @param authentication 当前用户认证信息
     * @return 是否是附件上传者
     */
    public boolean isUploader(Long attachmentId, Authentication authentication) {
        Long userId = currentUserIdProvider.get(authentication);
        Attachment attachment = attachmentMapper.selectById(attachmentId);

        if (attachmentId == null || attachment == null) {
            return false;
        }

        return Objects.equals(attachment.getUploaderId(), userId);
    }
}
