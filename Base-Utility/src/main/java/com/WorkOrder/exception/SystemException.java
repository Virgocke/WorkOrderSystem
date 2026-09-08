package com.WorkOrder.exception;

import com.WorkOrder.enums.SystemExceptionEnum;

/**
 * @author Virgor
 * @date 2026年09月08日 19:21
 * @description 系统异常
 */
public class SystemException extends RuntimeException{
    public SystemException(){
        super();
    }
    public SystemException(String message){
        super(message);
    }

    public String getErrMessage(){
        return super.getMessage();
    }

    public static void cast(SystemExceptionEnum commonError){
        throw new SystemException(commonError.getErrMessage());
    }
    public static void cast(String errMessage){
        throw new SystemException(errMessage);
    }
}
