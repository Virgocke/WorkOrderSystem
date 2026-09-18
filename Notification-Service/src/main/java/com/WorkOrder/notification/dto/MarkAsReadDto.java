package com.WorkOrder.notification.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月18日 23:44
 * @description 标记已读 DTO
 */
@Data
public class MarkAsReadDto {
    @NotEmpty
    private List<@NotNull @Positive Long> ids;
}
