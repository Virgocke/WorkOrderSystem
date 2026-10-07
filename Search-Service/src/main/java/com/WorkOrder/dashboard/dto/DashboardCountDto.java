package com.WorkOrder.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 仪表盘分组数量。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCountDto {

    /**
     * 前端展示的分组名称。
     */
    private String name;

    /**
     * 当前分组的工单数量。
     */
    private long value;
}
