package com.WorkOrder.dashboard.dto;

import lombok.Data;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.enums.SystemExceptionEnum;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** 审计筛选。时间按项目本地时间解释，结束分钟包含该分钟的全部秒。 */
@Data
public class AuditQuery {
    /** 页码，从 1 开始。 */
    private int page = 1;
    /** 每页条数，最多 100 条。 */
    private int pageSize = 15;
    /** 动作或内容关键字，按字面子串匹配。 */
    private String keyword;
    /** 工单编号筛选或响应值。 */
    private String ticketNo;
    /** 当前操作人姓名筛选，按字面子串匹配。 */
    private String operator;
    /** 系统配置键；筛选时精确匹配。 */
    private String configKey;
    /** 客户端开始时间，支持分钟或秒精度。 */
    private String start;
    /** 客户端结束时间，包含选定分钟或秒。 */
    private String end;
    /** 解析后的包含式开始时间。 */
    private LocalDateTime startAt;
    /** 解析后的排除式结束时间。 */
    private LocalDateTime endBefore;

    /** 校验页码、长度和时间范围，转义 LIKE 的通配符。 */
    public void validate() {
        if (page < 1 || pageSize < 1 || pageSize > 100) { invalid(); }
        keyword = literal(keyword);
        ticketNo = literal(ticketNo);
        operator = literal(operator);
        if (configKey != null) { configKey = configKey.trim(); }
        if (configKey != null && configKey.length() > 100) { invalid(); }
        try {
            startAt = parse(start);
            LocalDateTime endAt = parse(end);
            if (startAt != null && endAt != null && startAt.isAfter(endAt)) { invalid(); }
            endBefore = endAt == null ? null : (end.trim().length() == 16 ? endAt.plusMinutes(1) : endAt.plusSeconds(1));
        } catch (java.time.DateTimeException exception) { invalid(); }
    }

    /** 使用 long 计算 SQL 偏移量，避免 int 乘法溢出。 */
    public long getOffset() { return ((long) page - 1) * pageSize; }

    /** 严格解析本地时间，拒绝不存在的日期；空值表示不限制。 */
    private LocalDateTime parse(String value) {
        if (value == null || value.trim().isEmpty()) { return null; }
        String text = value.trim();
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(text.length() == 16
                ? "uuuu-MM-dd HH:mm" : "uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT));
    }

    /** 清理空白并转义 LIKE 特殊字符，以 ! 作为转义符。 */
    private String literal(String value) {
        if (value == null || value.trim().isEmpty()) { return null; }
        if (value.length() > 200) { invalid(); }
        return value.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** 统一返回项目约定的参数错误。 */
    private void invalid() { throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT); }
}
