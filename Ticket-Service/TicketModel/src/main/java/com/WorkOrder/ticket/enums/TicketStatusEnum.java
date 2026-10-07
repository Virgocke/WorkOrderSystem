package com.WorkOrder.ticket.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Virgor
 * @date 2026年09月08日 20:05
 * @description 工单状态枚举
 */
@Getter
public enum TicketStatusEnum {
    PENDING_ASSIGN(0, "PENDING_ASSIGN", "待分配_用户提交后默认状态，尚无处理人",
        1, 5), // 只能流转到"待响应"或"已撤销"

    PENDING_RESPONSE(1, "PENDING_RESPONSE", "待响应_已分配处理人，等待首次响应",
        2, 5),

    PROCESSING(2, "PROCESSING", "处理中_处理人已首次响应",
        3, 5),

    RESOLVED(3, "RESOLVED", "已解决_处理人已提交解决方案，语义上“待用户确认”",
        4, 1), // 用户若不满意可重新打开（驳回到待响应）

    CLOSED(4, "CLOSED", "已关闭_用户确认解决或管理员关闭",
        new Integer[0]), // 终态，不可再流转

    CANCELLED(5, "CANCELLED", "已撤销_用户/管理员撤销",
        new Integer[0]), // 终态，不可再流转
    ;

    private final int code; // 存进数据库的码
    private final String name; // 状态名称
    private final String description; // 状态描述
    private final List<Integer> nextStatusCodes; // 允许流转的下一个状态码列表

    /**
     * 定义工单状态及其允许转移到的后续状态。
     *
     * @param code 工单状态代码
     * @param name 工单状态名称
     * @param description 工单状态说明
     * @param nextStatusCodes 允许转移到的后续状态代码
     */
    TicketStatusEnum(int code, String name, String description, Integer... nextStatusCodes) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.nextStatusCodes = Arrays.asList(nextStatusCodes);
    }

    /**
     * 根据 code 获取枚举，用于数据库反序列化
     *
     * @param code 业务代码
     * @return 工单状态Enum
     */
    public static TicketStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TicketStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的工单状态 code: " + code);
    }

    /**
     * 校验当前状态是否能流转到目标状态
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    public boolean canTransitionTo(TicketStatusEnum target) {
        if (target == null) {
            return false;
        }
        // 如果状态相同，通常视为无效操作，返回 false（亦可返回 true，视业务而定）
        if (this == target) {
            return false;
        }
        return this.nextStatusCodes.contains(target.code);
    }

    /**
     * 获取允许流转的下一个状态。
     * 以状态码保存关系，避免枚举常量初始化阶段的非法前向引用。
     *
     * @return 工单状态Enum列表
     */
    public List<TicketStatusEnum> getNextStatuses() {
        if (nextStatusCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<TicketStatusEnum> statuses = new ArrayList<>(nextStatusCodes.size());
        for (Integer nextStatusCode : nextStatusCodes) {
            statuses.add(fromCode(nextStatusCode));
        }
        return Collections.unmodifiableList(statuses);
    }
}
