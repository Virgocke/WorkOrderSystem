package com.WorkOrder.notification.dto;

import lombok.Data;

import javax.validation.constraints.Positive;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月18日 23:44
 * @description 标记已读 DTO
 */
@Data
public class MarkAsReadDto {
    private List<@Positive Long> ids;
}
