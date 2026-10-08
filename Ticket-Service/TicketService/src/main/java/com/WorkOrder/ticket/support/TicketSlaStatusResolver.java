package com.WorkOrder.ticket.support;

import com.WorkOrder.ticket.model.Tickets;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年10月09日
 * @description 使用同一时点计算在办工单 SLA，查询条件与返回状态采用相同规则。
 */
public final class TicketSlaStatusResolver {

    private static final List<String> ACTIVE_STATUSES =
            Arrays.asList("PENDING_ASSIGN", "PENDING_RESPONSE", "PROCESSING");
    private static final List<String> SLA_STATUSES =
            Arrays.asList("NORMAL", "NEAR_TIMEOUT", "TIMEOUT", "ESCALATED");
    private static final int NEAR_TIMEOUT_MINUTES = 30;

    // 参数 {0} 为筛选值，{1} 为请求时点，{2} 为即将超时窗口上界。
    private static final String SQL_STATUS = "CASE "
            + "WHEN sla_status = 'ESCALATED' OR escalated_level > 0 THEN 'ESCALATED' "
            + "WHEN status IS NULL OR status NOT IN ('PENDING_ASSIGN', 'PENDING_RESPONSE', 'PROCESSING') "
            + "THEN COALESCE(sla_status, 'NORMAL') "
            + "WHEN ((status IN ('PENDING_ASSIGN', 'PENDING_RESPONSE') AND first_response_at IS NULL "
            + "AND response_deadline < {1}) OR resolution_deadline < {1}) THEN 'TIMEOUT' "
            + "WHEN ((status IN ('PENDING_ASSIGN', 'PENDING_RESPONSE') AND first_response_at IS NULL "
            + "AND response_deadline <= {2}) OR resolution_deadline <= {2}) THEN 'NEAR_TIMEOUT' "
            + "WHEN resolution_deadline IS NOT NULL OR (status IN ('PENDING_ASSIGN', 'PENDING_RESPONSE') "
            + "AND first_response_at IS NULL AND response_deadline IS NOT NULL) THEN 'NORMAL' "
            + "ELSE COALESCE(sla_status, 'NORMAL') END";

    private TicketSlaStatusResolver() {
    }

    /**
     * 校验可选的 SLA 状态筛选值。
     *
     * @param status 已去除两端空白的筛选值，null 表示不筛选
     * @return 是否为可接受的筛选值
     */
    public static boolean isValid(String status) {
        return status == null || SLA_STATUSES.contains(status);
    }

    /**
     * 在数据库分页之前应用实时 SLA 条件，禁止取当前页后再过滤。
     *
     * @param query 当前工单查询条件
     * @param status 已校验的 SLA 筛选值，null 时不增加条件
     * @param now 本次请求统一的上海本地时间
     */
    public static void applyFilter(LambdaQueryWrapper<Tickets> query, String status, LocalDateTime now) {
        if (status != null) {
            query.apply(SQL_STATUS + " = {0}", status, now, now.plusMinutes(NEAR_TIMEOUT_MINUTES));
        }
    }

    /**
     * 返回查询时点的 SLA 状态；终态与没有截止时间的历史记录保留已有状态。
     * 已响应工单只按解决截止时间计时，已升级工单继续显示升级状态。
     *
     * @param ticket 当前主库工单
     * @param now 与筛选 SQL 共用的请求时点
     * @return 用于接口展示和筛选的 SLA 状态编码
     */
    public static String resolve(Tickets ticket, LocalDateTime now) {
        String storedStatus = ticket.getSlaStatus() == null ? "NORMAL" : ticket.getSlaStatus();
        if ("ESCALATED".equals(storedStatus) || ticket.getEscalatedLevel() > 0) {
            return "ESCALATED";
        }
        if (!ACTIVE_STATUSES.contains(ticket.getStatus())) {
            return storedStatus;
        }
        LocalDateTime responseDeadline = "PROCESSING".equals(ticket.getStatus())
                || ticket.getFirstResponseAt() != null ? null : ticket.getResponseDeadline();
        LocalDateTime resolutionDeadline = ticket.getResolutionDeadline();
        if (responseDeadline == null && resolutionDeadline == null) {
            return storedStatus;
        }
        if (isBefore(responseDeadline, now) || isBefore(resolutionDeadline, now)) {
            return "TIMEOUT";
        }
        LocalDateTime nearTimeoutAt = now.plusMinutes(NEAR_TIMEOUT_MINUTES);
        if (isAtOrBefore(responseDeadline, nearTimeoutAt) || isAtOrBefore(resolutionDeadline, nearTimeoutAt)) {
            return "NEAR_TIMEOUT";
        }
        return "NORMAL";
    }

    /**
     * 判断一个存在的截止时间是否已经过去。
     *
     * @param deadline 可空的 SLA 截止时间
     * @param boundary 比较边界
     * @return 截止时间存在且严格早于边界时为 true
     */
    private static boolean isBefore(LocalDateTime deadline, LocalDateTime boundary) {
        return deadline != null && deadline.isBefore(boundary);
    }

    /**
     * 判断一个存在的截止时间是否进入包含上界的预警窗口。
     *
     * @param deadline 可空的 SLA 截止时间
     * @param boundary 预警窗口上界
     * @return 截止时间存在且不晚于上界时为 true
     */
    private static boolean isAtOrBefore(LocalDateTime deadline, LocalDateTime boundary) {
        return deadline != null && !deadline.isAfter(boundary);
    }
}
