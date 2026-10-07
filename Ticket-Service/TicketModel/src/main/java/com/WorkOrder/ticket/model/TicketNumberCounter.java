package com.WorkOrder.ticket.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 编号范围的事务计数器；一行对应一个前缀与格式化日期。
 */
@Data
@TableName("ticket_number_counters")
public class TicketNumberCounter {
    /**
     * 前缀与日期部分，例如 WO20260930。
     */
    @TableId(type = IdType.INPUT)
    private String scopeKey;

    /**
     * 本范围已分配的最大序号，不包含前导零。
     */
    private Long lastValue;

    /**
     * 计数范围首次使用时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最近一次递增时间。
     */
    private LocalDateTime updatedAt;
}
