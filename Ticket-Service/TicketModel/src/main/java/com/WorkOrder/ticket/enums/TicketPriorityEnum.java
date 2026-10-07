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



    /**
     * 定义工单优先级及其说明。
     *
     * @param code 优先级代码
     * @param description 优先级说明
     */
    TicketPriorityEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取优先级代码。
     *
     * @return 当前优先级对应的整数代码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取优先级说明。
     *
     * @return 当前优先级的说明
     */
    public String getDescription() {
        return description;
    }
}
