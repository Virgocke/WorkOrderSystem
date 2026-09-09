package com.WorkOrder.exception;

import com.WorkOrder.enums.SystemExceptionEnum;

/**
 * @author Virgor
 * @date 2026年09月08日 19:21
 * @description 系统异常
 */
public class SystemException extends RuntimeException{

    private SystemExceptionEnum error;

    public SystemException(SystemExceptionEnum error) {
        super(error.getErrMessage());
        this.error = error;
    }

    public SystemExceptionEnum getError() {
        return error;
    }

    public SystemException(){
        super();
    }
    public SystemException(String message){
        super(message);
    }

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
