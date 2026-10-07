package com.WorkOrder.search.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 控制记录与当前任务的同次 JOIN 结果，保留两侧字段以发现不一致。
 */
@Getter
@Setter
public class TicketSearchWriteTargetRecord {
    /**
     * 固定控制主键。
     */
    private Long controlId;
    /**
     * 控制记录登记的当前任务。
     */
    private Long currentJobId;
    /**
     * 控制记录登记的写代次。
     */
    private Long writeGeneration;
    /**
     * 控制记录登记的专属写别名。
     */
    private String writeAlias;
    /**
     * 共享同步阶段，决定目标是否已经登记。
     */
    private String phase;
    /**
     * 实际联接到的任务主键；缺失任务时为空。
     */
    private Long jobId;
    /**
     * 任务登记的代次。
     */
    private Long jobGeneration;
    /**
     * 任务登记的专属写别名。
     */
    private String jobWriteAlias;
    /**
     * 任务登记的物理索引。
     */
    private String targetIndex;
}
