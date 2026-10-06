package com.WorkOrder.search.exception;

import com.WorkOrder.search.model.TicketSearchBulkResult;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 批量写入部分失败；已成功的项目不会回滚，调用方可按失败 ID 重试。 */
public class TicketSearchBulkException extends IOException {

    /** 异常对象序列化的版本标识。 */
    private static final long serialVersionUID = 1L;

    /** 包含已写入、已覆盖及真正失败项的完整批次结果。 */
    private final TicketSearchBulkResult result;

    /** 失败文档 ID 与错误原因的只读映射，保留批量响应中的失败顺序。 */
    private final Map<String, String> failures;

    /**
     * 记录逐项分类结果；抛出异常不会撤销已完成的 Elasticsearch 写入。
     *
     * @param result 包含真正失败项目的批量结果
     */
    public TicketSearchBulkException(TicketSearchBulkResult result) {
        super("Elasticsearch 批量写入失败 " + result.getFailures().size() + " 条，写入 "
                + result.getAppliedCount() + " 条，版本覆盖 " + result.getCoveredCount() + " 条");
        this.result = result;
        Map<String, String> messages = new LinkedHashMap<>();
        result.getFailures().forEach((id, failure) -> messages.put(id, failure.getMessage()));
        this.failures = Collections.unmodifiableMap(messages);
    }

    /**
     * 获取本批次已成功写入且不会自动回滚的数量。
     *
     * @return 成功写入的文档数量
     */
    public int getSuccessCount() {
        return result.getAppliedCount();
    }

    /** @return 已被相同或更高版本覆盖的文档数量 */
    public int getCoveredCount() {
        return result.getCoveredCount();
    }

    /** @return 本次批量写入的完整逐项分类结果 */
    public TicketSearchBulkResult getResult() {
        return result;
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
