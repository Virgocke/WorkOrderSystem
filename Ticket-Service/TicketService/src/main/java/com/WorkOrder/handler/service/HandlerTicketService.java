package com.WorkOrder.handler.service;

import com.WorkOrder.handler.dto.HandlerTicketPageDto;
import com.WorkOrder.handler.dto.TicketNoteDto;
import com.WorkOrder.handler.dto.TransferTicketDto;
import com.WorkOrder.model.ticket.TicketResponse;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月16日 03:22
 * @description 处理人工单服务
 */
public interface HandlerTicketService {
    /**
     * 根据处理人ID获取工单列表
      * @param handlerId 处理人ID
     * @param handlerTicketPageDto 分页参数
     * @return 工单列表
     */
    List<TicketResponse> getHandlerTicket(Long handlerId, HandlerTicketPageDto handlerTicketPageDto);

    /**
     * 处理人首次响应
      * @param ticketId 工单ID
      * @param operatorId 当前操作人ID
      * @param operatorRole 当前登录角色，由后端认证信息取得
      * @param clientIp 客户端IP
      * @return 工单响应结果
     */
    TicketResponse firstResponse(Long ticketId, Long operatorId, String operatorRole, String clientIp);

    /**
     * 处理人解决工单
      * @param ticketId 工单ID
      * @param solution 解决方案
      * @return 工单响应结果
     */
    TicketResponse resolveTicket(Long ticketId, String solution, Long operatorId, String operatorRole, String clientIp);

    /**
     * 处理人转交工单
      * @param ticketId 工单ID
      * @param transferTicketDto 转交工单参数
      * @param operatorId 当前操作人ID
      * @param operatorRole 当前登录角色，由后端认证信息取得
      * @param clientIp 客户端IP
      * @return 工单响应结果
     */
    TicketResponse transferTicketToOtherHandler(Long ticketId, @Valid TransferTicketDto transferTicketDto, Long operatorId, String operatorRole, String clientIp);

    /**
     * 处理人升级工单
      * @param ticketId 工单ID
      * @param reason 升级原因
      * @param operatorId 当前操作人ID
      * @param operatorRole 当前登录角色，由后端认证信息取得
      * @param clientIp 客户端IP
      * @return 工单响应结果
     */
    TicketResponse escalateTicket(Long ticketId, String reason, Long operatorId, String operatorRole, String clientIp);

    /**
     * 处理人添加工单备注
      * @param ticketId 工单ID
      * @param ticketNoteDto 备注参数
      * @param operatorId 当前操作人ID
      * @param operatorRole 当前登录角色，由后端认证信息取得
      * @param clientIp 客户端IP
      * @return 工单响应结果
     */
    Boolean setTicketNote(Long ticketId, @Valid TicketNoteDto ticketNoteDto, Long operatorId, String operatorRole, String clientIp);
}
