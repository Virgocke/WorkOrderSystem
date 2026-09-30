package com.WorkOrder.ticket.service;

import com.WorkOrder.model.ticket.SlaDefaults;
import com.WorkOrder.ticket.mapper.TicketSlaConfigurationMapper;
import com.WorkOrder.ticket.model.TicketCategory;
import com.WorkOrder.ticket.model.Tickets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 仅在创建时确定 SLA 截止时间，配置变更不更新历史工单。 */
@Service
@RequiredArgsConstructor
public class TicketSlaService {
    private final TicketSlaConfigurationMapper configurationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 按分类覆盖值、系统配置、内置默认值的顺序确定新工单截止时间。
     * 两项时限独立继承系统配置，不继承父分类，也不随优先级缩放。
     * @param ticket 尚未保存的新工单
     * @param category 所选分类，null SLA 表示使用系统默认值
     * @param createdAt 本次工单创建时间，两项截止时间共用同一起点
     */
    public void initializeDeadlines(Tickets ticket, TicketCategory category, LocalDateTime createdAt) {
        Integer response = category.getDefaultResponseSla();
        Integer resolution = category.getDefaultResolutionSla();
        if (response == null || resolution == null) {
            SlaDefaults defaults = currentDefaults();
            response = response == null ? defaults.getResponseMin() : response;
            resolution = resolution == null ? defaults.getResolutionMin() : resolution;
        }
        ticket.setResponseDeadline(createdAt.plusMinutes(response));
        ticket.setResolutionDeadline(createdAt.plusMinutes(resolution));
    }

    /** @return 本次创建读取的默认值；数据库错误或非法配置不会静默降级 */
    private SlaDefaults currentDefaults() {
        String raw = configurationMapper.selectDefaultsJson();
        if (raw == null) {
            return SlaDefaults.defaults();
        }
        try {
            return SlaDefaults.fromJson(objectMapper.readTree(raw));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("数据库中的SLA默认值不合法", exception);
        }
    }
}
