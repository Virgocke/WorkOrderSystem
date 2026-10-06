package com.WorkOrder.search.model;

/** 版本化投影写入的完成结果；两种结果均允许本次同步正常结束。 */
public enum TicketSearchWriteOutcome {

    /** 当前完整投影已经写入目标索引；搜索可见性仍取决于 refresh。 */
    APPLIED,

    /** 目标索引已经保存相同或更高源版本，本次写入无需覆盖。 */
    COVERED
}
