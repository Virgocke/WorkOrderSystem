package com.WorkOrder.exception;

import java.io.Serializable;

/**
 * @author Virgor
 * @date 2026年09月08日 20:05
 * @description 系统错误响应类
 */

public class SystemErrorResponse implements Serializable {
    private String errMessage;

    /**
     * 构造错误提示响应。
     *
     * @param errMessage 返回给调用方的错误提示
     */
    public SystemErrorResponse(String errMessage){
        this.errMessage= errMessage;
    }

    /**
     * 获取错误提示。
     *
     * @return 当前错误提示
     */
    public String getErrMessage() {
        return errMessage;
    }

    /**
     * 设置错误提示。
     *
     * @param errMessage 返回给调用方的错误提示
     */
    public void setErrMessage(String errMessage) {
        this.errMessage = errMessage;
    }
}