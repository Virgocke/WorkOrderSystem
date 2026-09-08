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
    private Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    // 成功时调用
    public static <T> Result<T> success(T data) {
        return new Result<>(SystemExceptionEnum.SUCCESS.getCode(), SystemExceptionEnum.SUCCESS.getErrMessage(), data);
    }
    public static <T> Result<T> success() {
        return success(null);
    }
    
    // ==== 错误响应（直接传入枚举） ====
    public static <T> Result<T> error(SystemExceptionEnum SystemExceptionEnum) {
        return new Result<>(SystemExceptionEnum.getCode(), SystemExceptionEnum.getErrMessage(), null);
    }

    // ==== 错误响应（枚举 + 自定义消息，用于动态文案，比如附件大小限制） ====
    public static <T> Result<T> error(SystemExceptionEnum SystemExceptionEnum, String customMessage) {
        return new Result<>(SystemExceptionEnum.getCode(), customMessage, null);
    }
}
