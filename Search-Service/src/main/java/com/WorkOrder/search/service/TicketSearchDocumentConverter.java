package com.WorkOrder.search.service;

import com.WorkOrder.model.search.TicketSearchDocument;
import com.WorkOrder.search.model.TicketIndexSource;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 统一工单 ID、空值与业务时区；同一源版本始终转换为同一份完整投影。 */
public class TicketSearchDocumentConverter {
    private final ZoneId sourceZone;

    /** 显式提供 DATETIME 所属业务时区，不依赖 JVM 默认时区。 */
    public TicketSearchDocumentConverter(ZoneId sourceZone) {
        Assert.notNull(sourceZone, "工单源时间的业务时区不能为空");
        this.sourceZone = sourceZone;
    }

    /** 校验源行并转换完整文档；可空描述、处理人和响应截止时间保留 null。 */
    public TicketSearchDocument convert(TicketIndexSource source) {
        Assert.notNull(source, "工单搜索源行不能为空");
        validatePositive(source.getTicketId(), "ticketId");
        validatePositive(source.getCategoryId(), "categoryId");
        validatePositive(source.getCreatorId(), "creatorId");
        validatePositive(source.getSourceVersion(), "sourceVersion");
        if (source.getHandlerId() != null) {
            validatePositive(source.getHandlerId(), "handlerId");
        }
        Assert.hasText(source.getTicketNo(), "工单源 ticketNo 不能为空");
        Assert.hasText(source.getTitle(), "工单源 title 不能为空");
        Assert.hasText(source.getStatus(), "工单源 status 不能为空");
        Assert.hasText(source.getSlaStatus(), "工单源 slaStatus 不能为空");
        Assert.isTrue(source.getPriority() != null && source.getPriority() >= 1
                && source.getPriority() <= 4, "工单源 priority 必须在 1 到 4 之间");
        Assert.notNull(source.getCreatedAt(), "工单源 createdAt 不能为空");
        Assert.notNull(source.getUpdatedAt(), "工单源 updatedAt 不能为空");

        // 每次构造完整投影，而非只合并本次变动字段，避免转派、关闭后残留旧值。
        TicketSearchDocument document = new TicketSearchDocument();
        document.setTicketId(String.valueOf(source.getTicketId()));
        document.setTicketNo(source.getTicketNo());
        document.setTitle(source.getTitle());
        document.setDescription(source.getDescription());
        document.setCategoryId(String.valueOf(source.getCategoryId()));
        document.setPriority(source.getPriority());
        document.setStatus(source.getStatus());
        document.setCreatorId(String.valueOf(source.getCreatorId()));
        // 处理人变为 null 时完整替换会移除旧字段，不能把 null 转成字符串或沿用旧处理人。
        document.setHandlerId(source.getHandlerId() == null ? null : String.valueOf(source.getHandlerId()));
        document.setSlaStatus(source.getSlaStatus());
        document.setCreatedAt(toOffset(source.getCreatedAt()));
        document.setResponseDeadline(toOffset(source.getResponseDeadline()));
        document.setUpdatedAt(toOffset(source.getUpdatedAt()));
        document.setSourceVersion(source.getSourceVersion());
        return document;
    }

    /** 保留缺失时间，对存在的 DATETIME 使用业务时区计算偏移。 */
    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(sourceZone).toOffsetDateTime();
    }

    /** 拒绝非法源标识或版本，不能用零值掩盖源数据错误。 */
    private void validatePositive(Long value, String field) {
        Assert.isTrue(value != null && value > 0, "工单源 " + field + " 必须为正数");
    }
}
