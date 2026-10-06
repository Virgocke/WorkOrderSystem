package com.WorkOrder.search.model;

import org.springframework.util.Assert;

/** 本次投影请求固定的受管写目标；不可变别名防止在途请求写入其他代次。 */
public final class TicketSearchWriteTarget {
    private final long jobId;
    private final long generation;
    private final String physicalIndex;
    private final String writeAlias;

    /** 保存服务端校验后的任务、代次、物理索引和专属写别名。 */
    public TicketSearchWriteTarget(long jobId, long generation, String physicalIndex, String writeAlias) {
        Assert.isTrue(jobId > 0, "工单搜索任务 ID 必须为正数");
        Assert.isTrue(generation > 0, "工单搜索写代次必须为正数");
        Assert.hasText(physicalIndex, "工单搜索物理索引不能为空");
        Assert.hasText(writeAlias, "工单搜索写别名不能为空");
        this.jobId = jobId;
        this.generation = generation;
        this.physicalIndex = physicalIndex;
        this.writeAlias = writeAlias;
    }

    /** @return 登记此目标的导入任务 ID */
    public long getJobId() {
        return jobId;
    }

    /** @return 本次写入固定的代次 */
    public long getGeneration() {
        return generation;
    }

    /** @return 任务登记的物理索引 */
    public String getPhysicalIndex() {
        return physicalIndex;
    }

    /** @return 此代次不可变的专属写别名 */
    public String getWriteAlias() {
        return writeAlias;
    }
}
