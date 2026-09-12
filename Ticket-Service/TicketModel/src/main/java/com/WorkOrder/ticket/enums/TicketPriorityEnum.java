package com.WorkOrder.ticket.enums;

/**
 * @author Virgor
 * @date 2026年09月13日 03:00
 * @description 工单优先级枚举
 */
public enum TicketPriorityEnum {
    //1紧急、2高、3中、4低
    URGENT(1, "紧急"),
    HIGH(2, "高"),
    MEDIUM(3, "中"),
    LOW(4, "低");


    private final int code;
    private final String description;



    TicketPriorityEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
