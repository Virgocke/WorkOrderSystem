package com.WorkOrder.search.exception;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 批量写入部分失败；已成功的项目不会回滚，调用方可按失败 ID 重试。 */
public class TicketSearchBulkException extends IOException {

    /** 异常对象序列化的版本标识。 */
    private static final long serialVersionUID = 1L;

    /** 本批次已经成功写入的文档数量，抛出异常不会撤销这些写入。 */
    private final int successCount;

    /** 失败文档 ID 与错误原因的只读映射，保留批量响应中的失败顺序。 */
    private final Map<String, String> failures;

    /**
     * 记录批量写入的成功数量和逐条失败信息，复制失败映射以固定异常发生时的结果。
     *
     * @param successCount 已成功写入的文档数量
     * @param failures 文档 ID 到 Elasticsearch 错误原因的映射，不能为 null
     */
    public TicketSearchBulkException(int successCount, Map<String, String> failures) {
        super("Elasticsearch 批量写入失败 " + failures.size() + " 条，成功 " + successCount + " 条");
        this.successCount = successCount;
        this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
    }

    /**
     * 获取本批次已成功写入且不会自动回滚的数量。
     *
     * @return 成功写入的文档数量
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * 获取失败文档的 ID 与错误原因，供调用方定位失败项并安排重试。
     *
     * @return 按响应顺序保存的只读失败映射
     */
    public Map<String, String> getFailures() {
        return failures;
    }
}
