package com.WorkOrder.search.model;

import org.springframework.util.Assert;

/** 单张受管投影的实时版本；缺失与请求失败不同，失败不得转换为缺失。 */
public final class TicketSearchStoredVersion {

    private final long version;
    private final Long sourceVersion;
    private final boolean found;

    /**
     * 固定已校验的版本记录；存在时 ES 元数据版本必须与文档源版本一致。
     *
     * @param version 存在时为正数 ES _version，缺失时为 0
     * @param sourceVersion 存在时为对应正数 sourceVersion，缺失时为 null
     * @param found 是否找到文档
     */
    public TicketSearchStoredVersion(long version, Long sourceVersion, boolean found) {
        Assert.isTrue(found ? version > 0 && sourceVersion != null && sourceVersion == version
                : version == 0 && sourceVersion == null, "工单搜索已存版本记录不符合源版本协议");
        this.version = version;
        this.sourceVersion = sourceVersion;
        this.found = found;
    }

    /** @return 实时读取的 ES 外部版本，缺失时为 0 */
    public long getVersion() {
        return version;
    }

    /** @return 文档中的源版本，缺失时为 null */
    public Long getSourceVersion() {
        return sourceVersion;
    }

    /** @return 文档是否存在；网络和分片失败不会生成此记录 */
    public boolean isFound() {
        return found;
    }
}
