package com.WorkOrder.search.model.admin;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 诊断数据的可用性；采集失败必须表示未知，不能转换为零。
 */
public enum TicketSearchDiagnosticState {
    /**
     * 已成功读取当前观测值。
     */
    AVAILABLE,
    /**
     * 数据缺失、客户端未运行或采集失败。
     */
    UNKNOWN
}
