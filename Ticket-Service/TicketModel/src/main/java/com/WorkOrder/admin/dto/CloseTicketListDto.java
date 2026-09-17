package com.WorkOrder.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月18日 02:37
 * @description 关闭工单列表DTO
 */
@Data
public class CloseTicketListDto extends CloseTicketDto{
    private List<Long> ids;
}
