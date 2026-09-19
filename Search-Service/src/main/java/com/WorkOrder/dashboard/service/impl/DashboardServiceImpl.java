package com.WorkOrder.dashboard.service.impl;

import com.WorkOrder.dashboard.dto.DashboardCountDto;
import com.WorkOrder.dashboard.dto.DashboardGroupCountDto;
import com.WorkOrder.dashboard.dto.DashboardHandlerHeatRow;
import com.WorkOrder.dashboard.dto.DashboardOverviewDto;
import com.WorkOrder.dashboard.dto.DashboardSummaryDto;
import com.WorkOrder.dashboard.dto.DashboardTicketRow;
import com.WorkOrder.dashboard.dto.DashboardTrendDto;
import com.WorkOrder.dashboard.dto.DashboardTrendRow;
import com.WorkOrder.dashboard.mapper.DashboardMapper;
import com.WorkOrder.dashboard.service.DashboardService;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.ticket.TicketResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端全局仪表盘服务实现。 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 工单趋势统计的天数。 */
    private static final int TREND_DAYS = 14;

    /** 处理人负载热力图统计的天数。 */
    private static final int HANDLER_HEAT_DAYS = 7;

    /** 仪表盘日期统计使用的业务时区。 */
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Shanghai");

    /** 趋势与热力图日期标签格式。 */
    private static final DateTimeFormatter DATE_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd");

    /** 工单响应中的日期时间格式。 */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 工单状态编码与中文展示名称的映射。 */
    private static final Map<String, String> STATUS_LABELS = createStatusLabels();

    /** 工单优先级编码与中文展示名称的映射。 */
    private static final Map<String, String> PRIORITY_LABELS = createPriorityLabels();

    /** 仪表盘数据库查询组件。 */
    private final DashboardMapper dashboardMapper;

    /** 附件 URL JSON 反序列化组件。 */
    private final ObjectMapper objectMapper;

    /**
     * 在同一只读事务快照中组装仪表盘，避免多条统计查询的数据口径不一致。
     *
     * @return 管理端全局仪表盘数据
     */
    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewDto getOverview() {
        // 统计信息
        DashboardSummaryDto summary = dashboardMapper.selectSummary();
        if (summary == null) {
            summary = emptySummary();
        }

        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        List<LocalDate> trendDates = recentDates(today, TREND_DAYS);
        List<LocalDate> heatDates = recentDates(today, HANDLER_HEAT_DAYS);

        // 组装数据
        return DashboardOverviewDto.builder()
                // 基本统计信息
                .totalTickets(summary.getTotalTickets())
                // SLA合规率
                .pendingCount(summary.getPendingCount())
                // SLA合规率
                .slaComplianceRate(defaultDecimal(
                        summary.getSlaComplianceRate(), new BigDecimal("100.0")))
                // 响应时长
                .avgResponseMinutes(defaultDecimal(
                        summary.getAvgResponseMinutes(), BigDecimal.ZERO))
                // 解决时长
                .avgResolutionMinutes(defaultDecimal(
                        summary.getAvgResolutionMinutes(), BigDecimal.ZERO))
                // 状态分布
                .byStatus(toCountDtos(
                        dashboardMapper.selectStatusCounts(), STATUS_LABELS))
                // 优先级分布
                .byPriority(toCountDtos(
                        dashboardMapper.selectPriorityCounts(), PRIORITY_LABELS))
                // 趋势分布
                .trend(toTrendDtos(dashboardMapper.selectTrend(), trendDates))
                // 处理人负载热力图
                .handlerHeat(toHandlerHeat(
                        dashboardMapper.selectHandlerHeat(heatDates)))
                // 最近工单
                .recentTickets(toTicketResponses(
                        dashboardMapper.selectRecentTickets()))
                .build();
    }

    /**
     * 将数据库分组计数转换为前端展示对象。
     *
     * @param rows 数据库分组计数
     * @param labels 分组键与展示名称的映射
     * @return 前端分组数量列表
     */
    private List<DashboardCountDto> toCountDtos(
            List<DashboardGroupCountDto> rows,
            Map<String, String> labels) {

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(row -> new DashboardCountDto(
                        labels.getOrDefault(row.getGroupKey(), row.getGroupKey()),
                        row.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * 将每日趋势查询结果转换为连续日期列表，没有数据的日期补零。
     *
     * @param rows 数据库每日趋势结果
     * @param dates 需要返回的连续日期
     * @return 前端每日趋势列表
     */
    private List<DashboardTrendDto> toTrendDtos(
            List<DashboardTrendRow> rows,
            List<LocalDate> dates) {

        // 将数据库查询结果按日期分组
        Map<LocalDate, DashboardTrendRow> rowsByDate = rows == null
                ? Collections.emptyMap()
                : rows.stream().collect(Collectors.toMap(
                        DashboardTrendRow::getStatDate,
                        Function.identity(),
                        (left, right) -> right));

        List<DashboardTrendDto> result = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            DashboardTrendRow row = rowsByDate.get(date);
            result.add(new DashboardTrendDto(
                    date.format(DATE_LABEL_FORMATTER),
                    row == null ? 0L : row.getCreated(),
                    row == null ? 0L : row.getResolved()));
        }
        return result;
    }

    /**
     * 将处理人负载记录转换为文档约定的三元组。
     *
     * @param rows 数据库处理人负载记录
     * @return 处理人名称、日期、数量组成的三元组列表
     */
    private List<List<Object>> toHandlerHeat(List<DashboardHandlerHeatRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(row -> Arrays.<Object>asList(
                        row.getHandlerName(),
                        row.getStatDate().format(DATE_LABEL_FORMATTER),
                        row.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * 批量转换最新工单数据库记录。
     *
     * @param rows 最新未终结工单数据库记录
     * @return 通用工单响应列表
     */
    private List<TicketResponse> toTicketResponses(List<DashboardTicketRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toTicketResponse).collect(Collectors.toList());
    }

    /**
     * 将仪表盘工单查询结果转换为通用工单响应。
     *
     * @param row 工单数据库查询结果
     * @return 通用工单响应
     */
    private TicketResponse toTicketResponse(DashboardTicketRow row) {
        return TicketResponse.builder()
                .id(row.getId())
                .ticketNo(row.getTicketNo())
                .title(row.getTitle())
                .description(row.getDescription())
                .categoryId(row.getCategoryId())
                .categoryName(row.getCategoryName())
                .priority(row.getPriority())
                .status(row.getStatus())
                .creatorId(row.getCreatorId())
                .creatorName(row.getCreatorName())
                .handlerId(row.getHandlerId())
                .handlerName(row.getHandlerName())
                .createdAt(formatTime(row.getCreatedAt()))
                .assignedAt(formatTime(row.getAssignedAt()))
                .responseDeadline(formatTime(row.getResponseDeadline()))
                .resolutionDeadline(formatTime(row.getResolutionDeadline()))
                .firstResponseAt(formatTime(row.getFirstResponseAt()))
                .resolvedAt(formatTime(row.getResolvedAt()))
                .closedAt(formatTime(row.getClosedAt()))
                .slaStatus(row.getSlaStatus())
                .escalatedLevel(row.getEscalatedLevel())
                .remindCount(row.getRemindCount())
                .source(row.getSource())
                .attachmentUrls(parseAttachmentUrls(row.getAttachmentUrls()))
                .updatedAt(formatTime(row.getUpdatedAt()))
                .build();
    }

    /**
     * 将数据库中的附件 JSON 数组转换为 URL 列表。
     *
     * @param attachmentUrls 附件 URL JSON 数组文本
     * @return 附件 URL 列表；空值返回空列表
     */
    private List<String> parseAttachmentUrls(String attachmentUrls) {
        if (attachmentUrls == null || attachmentUrls.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    attachmentUrls, new TypeReference<List<String>>() { });
        } catch (IOException exception) {
            throw new SystemException(SystemExceptionEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 生成包含当天在内的连续日期列表，并按日期升序排列。
     *
     * @param today 统计截止日期
     * @param days 统计天数
     * @return 连续日期列表
     */
    private List<LocalDate> recentDates(LocalDate today, int days) {
        List<LocalDate> dates = new ArrayList<>(days);
        LocalDate firstDate = today.minusDays(days - 1L);
        for (int offset = 0; offset < days; offset++) {
            dates.add(firstDate.plusDays(offset));
        }
        return dates;
    }

    /**
     * 为可能为空的统计数值提供默认值。
     *
     * @param value 数据库统计值
     * @param defaultValue 默认值
     * @return 非空统计值
     */
    private BigDecimal defaultDecimal(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 按接口文档约定格式化日期时间。
     *
     * @param time 待格式化日期时间
     * @return 格式化后的日期时间；输入为空时返回空
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }

    /**
     * 创建一个空的统计信息对象。
     * @return 空的统计信息对象
     */
    private DashboardSummaryDto emptySummary() {
        DashboardSummaryDto summary = new DashboardSummaryDto();
        summary.setSlaComplianceRate(new BigDecimal("100.0"));
        summary.setAvgResponseMinutes(BigDecimal.ZERO);
        summary.setAvgResolutionMinutes(BigDecimal.ZERO);
        return summary;
    }

    /**
     * 创建工单状态编码与中文展示名称的不可变映射。
     *
     * @return 工单状态展示名称映射
     */
    private static Map<String, String> createStatusLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("PENDING_ASSIGN", "待分配");
        labels.put("PENDING_RESPONSE", "待响应");
        labels.put("PROCESSING", "处理中");
        labels.put("RESOLVED", "已解决");
        labels.put("CLOSED", "已关闭");
        labels.put("CANCELLED", "已撤销");
        return Collections.unmodifiableMap(labels);
    }

    /**
     * 创建工单优先级编码与中文展示名称的不可变映射。
     *
     * @return 工单优先级展示名称映射
     */
    private static Map<String, String> createPriorityLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("1", "紧急");
        labels.put("2", "高");
        labels.put("3", "中");
        labels.put("4", "低");
        return Collections.unmodifiableMap(labels);
    }
}
