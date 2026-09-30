package com.WorkOrder.model.ticket;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 工单编号规则；前端日期格式由这里显式映射，避免 Java 的 YYYY 周年含义。 */
public final class TicketNumberRule {
    public static final String DEFAULT_JSON = "{\"prefix\":\"WO\",\"dateFormat\":\"YYYYMMDD\",\"seqLength\":4}";

    private final String prefix;
    private final String dateFormat;
    private final int seqLength;

    private TicketNumberRule(String prefix, String dateFormat, int seqLength) {
        this.prefix = prefix;
        this.dateFormat = dateFormat;
        this.seqLength = seqLength;
    }

    /**
     * 校验管理员保存或数据库读取的完整规则。
     * @param value 包含 prefix、dateFormat、seqLength 的 JSON 对象
     * @return 已校验的编号规则
     */
    public static TicketNumberRule fromJson(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 3
                || !value.has("prefix") || !value.has("dateFormat") || !value.has("seqLength")) {
            throw new IllegalArgumentException("工单编号规则必须包含 prefix、dateFormat、seqLength");
        }
        JsonNode prefixNode = value.get("prefix");
        JsonNode formatNode = value.get("dateFormat");
        JsonNode lengthNode = value.get("seqLength");
        if (!prefixNode.isTextual() || !prefixNode.textValue().matches("[A-Z][A-Z0-9]{0,7}")) {
            throw new IllegalArgumentException("工单编号前缀须以大写字母开头，长度为1至8位，且仅包含大写字母和数字");
        }
        if (!formatNode.isTextual() || !("YYYYMMDD".equals(formatNode.textValue())
                || "YYMMDD".equals(formatNode.textValue()) || "YYYY".equals(formatNode.textValue()))) {
            throw new IllegalArgumentException("工单编号日期格式仅支持 YYYYMMDD、YYMMDD 或 YYYY");
        }
        if (!lengthNode.isIntegralNumber() || !lengthNode.canConvertToInt()
                || lengthNode.intValue() < 3 || lengthNode.intValue() > 6) {
            throw new IllegalArgumentException("工单编号序号位数须为3至6位整数");
        }
        return new TicketNumberRule(prefixNode.textValue(), formatNode.textValue(), lengthNode.intValue());
    }

    /** @return 尚未持久化配置时采用的默认规则 */
    public static TicketNumberRule defaults() {
        return new TicketNumberRule("WO", "YYYYMMDD", 4);
    }

    /**
     * 生成计数范围，同一前缀和日期部分始终沿用一个递增计数器。
     * @param date 亚洲上海时区的工单创建日期
     * @return 前缀与日期组成的计数范围键
     */
    public String scopeKey(LocalDate date) {
        String javaPattern;
        switch (dateFormat) {
            case "YYYYMMDD":
                javaPattern = "yyyyMMdd";
                break;
            case "YYMMDD":
                javaPattern = "yyMMdd";
                break;
            default:
                javaPattern = "yyyy";
        }
        return prefix + date.format(DateTimeFormatter.ofPattern(javaPattern));
    }

    /** @return 当前序号位数允许的最大值 */
    public int maxSequence() {
        int limit = 1;
        for (int index = 0; index < seqLength; index++) {
            limit *= 10;
        }
        return limit - 1;
    }

    /**
     * 生成带补零序号的完整编号，达到位数上限时拒绝扩位。
     * @param scopeKey 前缀与日期组成的计数范围键
     * @param sequence 已分配的正整数序号
     * @return 最终工单编号
     */
    public String format(String scopeKey, long sequence) {
        if (sequence < 1 || sequence > maxSequence()) {
            throw new IllegalArgumentException("工单编号序号超出配置位数");
        }
        // 序号转字符串，不足位数时在左侧补零
        String digits = Long.toString(sequence);
        // 拼接前缀、日期与序号
        StringBuilder number = new StringBuilder(scopeKey);
        for (int index = digits.length(); index < seqLength; index++) {
            number.append('0');
        }
        return number.append(digits).toString();
    }
}
