package com.WorkOrder.search.model;

import lombok.Data;

/** 主库中唯一的工单搜索控制记录；所有实例以此判断当前代次。 */
@Data
public class TicketSearchControlState {
    /** 固定主键 1。 */
    private Long id;
    /** 当前导入任务。 */
    private Long currentJobId;
    /** 当前增量写入代次，目标准备完成前为空。 */
    private Long writeGeneration;
    /** 当前不可变专属写别名。 */
    private String writeAlias;
    /** 最后确认发布的代次，重建时保留。 */
    private Long publishedGeneration;
    /** 共享阶段。 */
    private String phase;
    /** 不含正文、地址或凭据的故障分类。 */
    private String lastError;
}
