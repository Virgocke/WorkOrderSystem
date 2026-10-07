package com.WorkOrder.search.model;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 一个批次的逐项分类结果；真正失败项与已写入、已被新版覆盖的项目分别记录。
 */
public final class TicketSearchBulkResult {

    /**
     * 本次已写入完整投影的数量。
     */
    private final int appliedCount;

    /**
     * 目标已有相同或更高源版本的数量。
     */
    private final int coveredCount;

    /**
     * 仍未完成的文档及错误分类，保持响应顺序且禁止调用方修改。
     */
    private final Map<String, Failure> failures;

    /**
     * 固定本批次结果，不保留调用方可变映射。
     *
     * @param appliedCount 实际写入数量，不得为负数
     * @param coveredCount 已有相同或更高版本的数量，不得为负数
     * @param failures 文档 ID 到失败分类的映射，不得为 null
     */
    public TicketSearchBulkResult(int appliedCount, int coveredCount, Map<String, Failure> failures) {
        Assert.isTrue(appliedCount >= 0 && coveredCount >= 0, "批量完成数量不能为负数");
        Assert.notNull(failures, "批量失败信息不能为空");
        this.appliedCount = appliedCount;
        this.coveredCount = coveredCount;
        this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
    }

    /**
     * 获取本次已写入完整投影的数量。
     *
     * @return 本次实际写入的文档数量
     */
    public int getAppliedCount() {
        return appliedCount;
    }

    /**
     * 获取目标已有相同或更高源版本的数量。
     *
     * @return 已被相同或更高版本覆盖的文档数量
     */
    public int getCoveredCount() {
        return coveredCount;
    }

    /**
     * 取得实际写入与同版或高版覆盖的总数量。
     *
     * @return 已完成投影同步的数量，包含实际写入及版本覆盖
     */
    public int getCompletedCount() {
        return appliedCount + coveredCount;
    }

    /**
     * 获取仍未完成的文档及错误分类，保持响应顺序且禁止调用方修改。
     *
     * @return 按响应顺序保存的只读失败分类
     */
    public Map<String, Failure> getFailures() {
        return failures;
    }

    /**
     * 判断本批是否仍有未完成的失败项。
     *
     * @return 本批次是否还有真正失败的项目
     */
    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /**
     * @author Virgor
     * @date 2026年10月07日
     * @description Elasticsearch 返回的单项失败，保留状态码、错误类型及原因供任务恢复使用。
     */
    public static final class Failure {

        /**
         * Elasticsearch 单项响应状态码。
         */
        private final int status;

        /**
         * Elasticsearch 错误类型；无法识别时使用 exception。
         */
        private final String type;

        /**
         * 服务端失败原因，仅供内部诊断，不应直接暴露给业务接口。
         */
        private final String message;

        /**
         * 创建不可变单项失败描述。
         *
         * @param status 单项 HTTP 状态码
         * @param type 错误类型
         * @param message 失败原因
         */
        public Failure(int status, String type, String message) {
            this.status = status;
            this.type = type;
            this.message = message;
        }

        /**
         * 获取Elasticsearch 单项响应状态码。
         *
         * @return 单项 HTTP 状态码
         */
        public int getStatus() {
            return status;
        }

        /**
         * 获取Elasticsearch 错误类型；无法识别时使用 exception。
         *
         * @return Elasticsearch 错误类型
         */
        public String getType() {
            return type;
        }

        /**
         * 获取服务端失败原因，仅供内部诊断，不应直接暴露给业务接口。
         *
         * @return 内部失败原因
         */
        public String getMessage() {
            return message;
        }
    }
}
