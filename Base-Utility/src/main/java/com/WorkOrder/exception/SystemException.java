package com.WorkOrder.exception;

import com.WorkOrder.enums.SystemExceptionEnum;

/**
 * @author Virgor
 * @date 2026年09月08日 19:21
 * @description 系统异常
 */
public class SystemException extends RuntimeException{

    private SystemExceptionEnum error;

    /**
     * 构造系统业务异常。
     *
     * @param error 业务错误枚举，提供错误码和默认提示
     */
    public SystemException(SystemExceptionEnum error) {
        super(error.getErrMessage());
        this.error = error;
    }

    /**
     * 获取业务错误枚举。
     *
     * @return 业务错误枚举；未使用枚举构造异常时可能为 null
     */
    public SystemExceptionEnum getError() {
        return error;
    }

    /**
     * 构造系统业务异常。
     */
    public SystemException(){
        super();
    }
    /**
     * 构造系统业务异常。
     *
     * @param message 自定义错误提示
     */
    public SystemException(String message){
        super(message);
    }

    /**
     * 获取异常提示。
     *
     * @return 异常的提示文本
     */
    public String getErrMessage(){
        return super.getMessage();
    }

    /**
     * 按系统错误枚举抛出异常，并保留错误码供统一异常处理器生成响应。
     *
     * @param error 系统错误枚举
     */
    public static void cast(SystemExceptionEnum error){
        throw new SystemException(error);
    }

    /**
     * 按自定义错误信息抛出系统异常。
     *
     * @param errMessage 错误信息
     */
    public static void cast(String errMessage){
        throw new SystemException(errMessage);
    }
}
