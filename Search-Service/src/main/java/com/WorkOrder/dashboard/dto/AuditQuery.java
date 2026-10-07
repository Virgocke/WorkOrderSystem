package com.WorkOrder.dashboard.dto;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import lombok.Data;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 审计筛选。时间按项目本地时间解释，结束分钟包含该分钟的全部秒。
 */
@Data
public class AuditQuery {
    /**
     * 页码，从 1 开始。
     */
    private int page = 1;
    /**
     * 每页条数，最多 100 条。
     */
    private int pageSize = 15;
    /**
     * 工单动作、内容或配置键、前后值的关键字，按字面子串匹配。
     */
    private String keyword;
    /**
     * 当前工单编号，按字面子串筛选。
     */
    private String ticketNo;
    /**
     * 当前操作人姓名筛选，按字面子串匹配。
     */
    private String operator;
    /**
     * 系统配置键；筛选时精确匹配。
     */
    private String configKey;
    /**
     * 客户端开始时间，支持分钟或秒精度。
     */
    private String start;
    /**
     * 客户端结束时间，包含选定分钟或秒。
     */
    private String end;
    /**
     * 解析后的包含式开始时间。
     */
    private LocalDateTime startAt;
    /**
     * 解析后的排除式结束时间。
     */
    private LocalDateTime endBefore;

    /**
     * 校验页码、长度和时间范围，保留字面条件；可重复校验而不重复转义。
     */
    public void validate() {
        validatePagination();
        keyword = normalizeTextFilter(keyword);
        ticketNo = normalizeTextFilter(ticketNo);
        operator = normalizeTextFilter(operator);
        if (configKey != null) {
            configKey = configKey.trim();
        }
        if (configKey != null && configKey.length() > 100) {
            rejectInvalidQuery();
        }
        validateAndParseTimeRange();
    }

    /**
     * 使用 long 计算 SQL 偏移量，避免 int 乘法溢出。
     *
     * @return 按页码和页大小以 long 计算的零起始记录偏移量
     */
    public long getOffset() {
        return ((long) page - 1) * pageSize;
    }

    /**
     * SQL 和 ES 查询共用页码与每页条数限制。
     */
    private void validatePagination() {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            rejectInvalidQuery();
        }
    }

    /**
     * 从客户端文本重新解析时间范围，结束边界转换为 SQL 和 ES 共用的排除式时间。
     */
    private void validateAndParseTimeRange() {
        try {
            startAt = parseClientTime(start);
            LocalDateTime endAt = parseClientTime(end);
            if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
                rejectInvalidQuery();
            }
            endBefore = toExclusiveEndTime(endAt);
        } catch (DateTimeException exception) {
            rejectInvalidQuery();
        }
    }

    /**
     * 包含式的结束分钟或秒向后推进一个单位，空结束条件不生成时间上限。
     *
     * @param endAt 已解析的包含式结束分钟或结束秒，可为 null
     * @return 向后推进对应精度单位的排除式时间上限；未指定结束时间时为 null
     */
    private LocalDateTime toExclusiveEndTime(LocalDateTime endAt) {
        if (endAt == null) {
            return null;
        }
        boolean minutePrecision = end.trim().length() == 16;
        return minutePrecision ? endAt.plusMinutes(1) : endAt.plusSeconds(1);
    }

    /**
     * 严格解析本地时间，拒绝不存在的日期；空值表示不限制。
     *
     * @param value 客户端本地时间文本，精度为分钟或秒，可为空
     * @return 严格解析的本地日期时间；空值或空白文本时为 null
     */
    private LocalDateTime parseClientTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        String pattern = text.length() == 16 ? "uuuu-MM-dd HH:mm" : "uuuu-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                .withResolverStyle(ResolverStyle.STRICT);
        return LocalDateTime.parse(text, formatter);
    }

    /**
     * 复制客户端字段后重新校验，不复用客户端传入的解析时间，也不修改原条件。
     *
     * @return 复制客户端字段并重新校验、解析时间范围的独立查询条件
     */
    public AuditQuery normalizedCopy() {
        AuditQuery copy = new AuditQuery();
        copy.setPage(page);
        copy.setPageSize(pageSize);
        copy.setKeyword(keyword);
        copy.setTicketNo(ticketNo);
        copy.setOperator(operator);
        copy.setConfigKey(configKey);
        copy.setStart(start);
        copy.setEnd(end);
        copy.validate();
        return copy;
    }

    /**
     * 获取仅供 Mapper 使用的字面工单编号，不改变原始查询条件。
     *
     * @return 仅供 SQL LIKE 字面匹配的工单编号，已转义 !、% 和 _；未指定时为 null
     */
    public String getSqlTicketNo() {
        return escapeSqlLikeLiteral(ticketNo);
    }

    /**
     * 获取仅供 Mapper 使用的字面操作人姓名，不改变原始查询条件。
     *
     * @return 仅供 SQL LIKE 字面匹配的操作人姓名，已转义 !、% 和 _；未指定时为 null
     */
    public String getSqlOperator() {
        return escapeSqlLikeLiteral(operator);
    }

    /**
     * 清理空白并限制输入长度，ES 关键词保持用户输入的字面内容。
     *
     * @param value 尚未规范化的客户端字面筛选文本
     * @return 去除首尾空白的字面文本；空值或空白时为 null
     */
    private String normalizeTextFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if (value.length() > 200) {
            rejectInvalidQuery();
        }
        return value.trim();
    }

    /**
     * 在 SQL 参数边界转义 LIKE 通配符，以 ! 作为转义符。
     *
     * @param value 需要按字面值匹配的 SQL LIKE 参数，可为 null
     * @return 以 ! 为转义符保护 !、% 和 _ 的文本；输入为 null 时为 null
     */
    private String escapeSqlLikeLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * 统一返回项目约定的参数错误。
     */
    private void rejectInvalidQuery() {
        throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
    }
}
