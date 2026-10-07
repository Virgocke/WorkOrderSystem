package com.WorkOrder.notification.dto;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.page.PageParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理员邮件任务筛选，创建时间使用项目本地时间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmailDeliveryAdminQuery extends PageParams {
    /**
     * 默认失败任务；ALL 表示不限制投递状态。
     */
    private String status = "FAILED";
    /**
     * 包含式创建时间起点，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String start;
    /**
     * 包含式创建时间终点，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String end;
    /**
     * 仅供数据库使用，服务重新解析客户端文本。
     */
    private LocalDateTime startAt;
    /**
     * 结束秒之后的排除式边界。
     */
    private LocalDateTime endBefore;

    /**
     * 沿用公共分页参数，邮件管理页默认每页二十条。
     */
    public EmailDeliveryAdminQuery() {
        setPageSize(20L);
    }

    /**
     * 复制允许的输入字段，避免客户端绑定派生时间条件。
     *
     * @return 重新校验页码、状态及起止时间后的新查询对象，忽略客户端传入的派生时间字段
     */
    public EmailDeliveryAdminQuery normalizedCopy() {
        EmailDeliveryAdminQuery copy = new EmailDeliveryAdminQuery();
        copy.setPage(getPage());
        copy.setPageSize(getPageSize());
        copy.setStatus(status);
        copy.setStart(start);
        copy.setEnd(end);
        copy.validate();
        return copy;
    }

    /**
     * 校验页码、状态和时间范围；状态为空时保持默认失败筛选。
     */
    public void validate() {
        if (getPage() == null || getPageSize() == null || getPage() < 1
                || getPageSize() < 1 || getPageSize() > 100) {
            invalid();
        }
        try {
            Math.multiplyExact(getPage() - 1, getPageSize());
        } catch (ArithmeticException exception) {
            invalid();
        }
        status = status == null || status.trim().isEmpty() ? "FAILED" : status.trim();
        if (!Arrays.asList("ALL", "PENDING", "SENDING", "RETRY", "SENT", "FAILED").contains(status)) {
            invalid();
        }
        try {
            startAt = parseTime(start);
            LocalDateTime endAt = parseTime(end);
            if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
                invalid();
            }
            endBefore = endAt == null ? null : endAt.plusSeconds(1);
        } catch (DateTimeException exception) {
            invalid();
        }
    }

    /**
     * 返回已校验的 long 偏移量。
     *
     * @return 从 0 开始的 SQL 分页偏移量，使用 long 精确乘法计算
     */
    public long getOffset() {
        return Math.multiplyExact(getPage() - 1, getPageSize());
    }

    /**
     * 严格拒绝不存在的日期，空时间表示不限制。
     *
     * @param text 客户端本地时间文本，格式 yyyy-MM-dd HH:mm:ss，可为空
     * @return 严格解析的本地时间；输入为空或仅含空白时为 null
     */
    private LocalDateTime parseTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                .withResolverStyle(ResolverStyle.STRICT));
    }

    /**
     * 使用公共参数错误响应。
     */
    private void invalid() {
        throw new SystemException(SystemExceptionEnum.ILLEGAL_ARGUMENT);
    }
}
