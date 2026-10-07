package com.WorkOrder.model;

import com.WorkOrder.enums.SystemExceptionEnum;
import lombok.Data;

/**
 * @author Virgor
 * @date 2026年09月08日 19:44
 * @description 统一返回结果
 */
@Data
public class Result<T> {
    // 状态码，0-成功，非0-失败
    private int code;
    // 提示信息，成功时为success，失败时为error
    private String message;
    // 返回数据
    private T data;

    // 私有构造器，强制使用静态方法创建
    /**
     * 构造统一响应。
     */
    private Result() {}

    /**
     * 构造统一响应。
     *
     * @param code 业务响应码
     * @param message 业务响应提示
     * @param data 响应数据
     */
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    // 成功时调用
    /**
     * 构造成功响应。
     *
     * @param <T> 本方法使用的泛型类型
     * @param data 成功响应携带的数据
     * @return 使用成功业务码的统一响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SystemExceptionEnum.SUCCESS.getCode(), SystemExceptionEnum.SUCCESS.getErrMessage(), data);
    }
    /**
     * 构造成功响应。
     *
     * @param <T> 本方法使用的泛型类型
     * @return 使用成功业务码的统一响应
     */
    public static <T> Result<T> success() {
        return success(null);
    }
    
    // ==== 错误响应（直接传入枚举） ====
    /**
     * 构造业务错误响应。
     *
     * @param <T> 本方法使用的泛型类型
     * @param SystemExceptionEnum 业务错误枚举
     * @return 携带业务错误码和提示的统一响应
     */
    public static <T> Result<T> error(SystemExceptionEnum SystemExceptionEnum) {
        return new Result<>(SystemExceptionEnum.getCode(), SystemExceptionEnum.getErrMessage(), null);
    }

    // ==== 错误响应（枚举 + 自定义消息，用于动态文案，比如附件大小限制） ====
    /**
     * 构造业务错误响应。
     *
     * @param <T> 本方法使用的泛型类型
     * @param SystemExceptionEnum 业务错误枚举
     * @param customMessage 覆盖默认提示的错误文本
     * @return 携带业务错误码和提示的统一响应
     */
    public static <T> Result<T> error(SystemExceptionEnum SystemExceptionEnum, String customMessage) {
        return new Result<>(SystemExceptionEnum.getCode(), customMessage, null);
    }
}
