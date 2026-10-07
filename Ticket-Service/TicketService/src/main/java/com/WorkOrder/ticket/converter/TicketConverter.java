package com.WorkOrder.ticket.converter;

import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.ticket.model.Tickets;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单实体与响应对象之间的转换。
 */
public final class TicketConverter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 禁止实例化工单响应转换工具类。
     */
    private TicketConverter() {
    }

    /**
     * 将工单实体转换为新的响应对象。
     *
     * @param ticket 工单
     * @return 工单详情
     */
    public static TicketResponse toResponse(Tickets ticket) {
        return toResponse(ticket, new TicketResponse());
    }

    /**
     * 将工单实体及关联的展示名称转换为响应对象。
     *
     * <p>分类名称和用户名称不存储在 tickets 表中，因此由调用方查询后传入。</p>
     *
     * @param ticket 工单
     * @param categoryName 分类名称
     * @param creatorName 创建人名称
     * @param handlerName 处理人名称
     * @return 工单详情
     */
    public static TicketResponse toResponse(
            Tickets ticket,
            String categoryName,
            String creatorName,
            String handlerName) {
        TicketResponse response = toResponse(ticket);
        response.setCategoryName(categoryName);
        response.setCreatorName(creatorName);
        response.setHandlerName(handlerName);
        return response;
    }

    /**
     * 填充已有响应对象，保留分类名称、创建人名称等实体中没有的属性。
     * 创建和更新时间等 LocalDateTime 字段统一转换为接口约定的时间字符串。
     *
     * @param ticket 工单
     * @param response 工单详情
     * @return 工单详情
     */
    public static TicketResponse toResponse(Tickets ticket, TicketResponse response) {
        Objects.requireNonNull(ticket, "工单不能为空");
        Objects.requireNonNull(response, "响应对象不能为空");

        BeanUtils.copyProperties(
                ticket,
                response,
                "createdAt",
                "updatedAt",
                "assignedAt",
                "responseDeadline",
                "resolutionDeadline",
                "firstResponseAt",
                "resolvedAt",
                "closedAt"
        );

        response.setCreatedAt(formatTime(ticket.getCreatedAt()));
        response.setUpdatedAt(formatTime(ticket.getUpdatedAt()));
        response.setAssignedAt(formatTime(ticket.getAssignedAt()));
        response.setResponseDeadline(formatTime(ticket.getResponseDeadline()));
        response.setResolutionDeadline(formatTime(ticket.getResolutionDeadline()));
        response.setFirstResponseAt(formatTime(ticket.getFirstResponseAt()));
        response.setResolvedAt(formatTime(ticket.getResolvedAt()));
        response.setClosedAt(formatTime(ticket.getClosedAt()));
        if (response.getAttachmentUrls() == null) {
            response.setAttachmentUrls(Collections.emptyList());
        }

        return response;
    }

    /**
     * 将日期时间格式化为工单接口的时间文本。
     *
     * @param time 待格式化的日期时间，可以为 null
     * @return 格式化后的时间文本；原时间为 null 时返回 null
     */
    private static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }
}
