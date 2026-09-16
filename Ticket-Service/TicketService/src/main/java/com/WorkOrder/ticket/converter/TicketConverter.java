package com.WorkOrder.ticket.converter;

import com.WorkOrder.model.ticket.TicketResponse;
import com.WorkOrder.ticket.model.Tickets;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 工单实体与响应对象之间的转换。
 */
public final class TicketConverter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TicketConverter() {
    }

    /**
     * 将工单实体转换为新的响应对象。
     */
    public static TicketResponse toResponse(Tickets ticket) {
        return toResponse(ticket, new TicketResponse());
    }

    /**
     * 填充已有响应对象，保留分类名称、创建人名称等实体中没有的属性。
     */
    public static TicketResponse toResponse(Tickets ticket, TicketResponse response) {
        Objects.requireNonNull(ticket, "工单不能为空");
        Objects.requireNonNull(response, "响应对象不能为空");

        BeanUtils.copyProperties(
                ticket,
                response,
                "responseDeadline",
                "resolutionDeadline",
                "firstResponseAt",
                "resolvedAt",
                "closedAt"
        );

        response.setResponseDeadline(formatTime(ticket.getResponseDeadline()));
        response.setResolutionDeadline(formatTime(ticket.getResolutionDeadline()));
        response.setFirstResponseAt(formatTime(ticket.getFirstResponseAt()));
        response.setResolvedAt(formatTime(ticket.getResolvedAt()));
        response.setClosedAt(formatTime(ticket.getClosedAt()));

        return response;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }
}
