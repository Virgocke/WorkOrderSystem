package com.WorkOrder.search.support;

import org.elasticsearch.ElasticsearchException;

/** 精确读取 Elasticsearch 高层客户端返回的顶层错误类型，不以状态码或原因文本代替类型。 */
public final class ElasticsearchFailureClassifier {

    /** HLRC 7.12.1 解析 error.type 后生成的固定消息前缀。 */
    private static final String PARSED_TYPE_PREFIX = "Elasticsearch exception [type=";

    /** 工具类不允许实例化。 */
    private ElasticsearchFailureClassifier() {
    }

    /**
     * 获取服务端返回的顶层错误类型，不检查 reason 文本、根因列表或嵌套 cause。
     * 7.12.1 高层客户端将 JSON error.type 写进固定消息前缀，不保留对应的 Java 异常子类。
     *
     * @param failure 高层客户端抛出的异常或 Bulk 单项失败原因
     * @return 服务端错误类型，无法识别时为 exception
     */
    public static String errorType(Throwable failure) {
        if (failure == null) {
            return "exception";
        }
        // 只取顶层 error.type；reason 或嵌套原因中的相同字样不能证明本次是可忽略的版本冲突。
        String message = failure.getMessage();
        if (failure instanceof ElasticsearchException && message != null
                && message.startsWith(PARSED_TYPE_PREFIX)) {
            int end = message.indexOf(", reason=", PARSED_TYPE_PREFIX.length());
            if (end > PARSED_TYPE_PREFIX.length()) {
                return message.substring(PARSED_TYPE_PREFIX.length(), end);
            }
        }
        return failure instanceof ElasticsearchException ? ElasticsearchException.getExceptionName(failure)
                : "exception";
    }

    /**
     * 精确比较顶层错误类型，调用方仍需结合相应 API 的状态码和请求前提判断结果。
     *
     * @param failure 高层客户端异常或 Bulk 单项失败原因
     * @param type Elasticsearch 错误类型
     * @return 顶层类型是否一致
     */
    public static boolean hasType(Throwable failure, String type) {
        return type != null && type.equals(errorType(failure));
    }
}
