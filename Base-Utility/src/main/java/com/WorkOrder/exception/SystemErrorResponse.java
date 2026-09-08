package com.WorkOrder.exception;

import java.io.Serializable;

/**
 * @author Virgor
 * @date 2026年09月08日 20:05
 * @description 系统错误响应类
 */

public class SystemErrorResponse implements Serializable {
    private String errMessage;

    public SystemErrorResponse(String errMessage){
        this.errMessage= errMessage;
    }

    public String getErrMessage() {
        return errMessage;
    }

    public void setErrMessage(String errMessage) {
        this.errMessage = errMessage;
    }
}