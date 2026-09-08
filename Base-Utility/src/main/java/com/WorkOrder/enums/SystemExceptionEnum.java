package com.WorkOrder.enums;

import lombok.Getter;

/**
 * @author Virgor
 * @date 2026年09月08日 19:45
 * @description 系统异常枚举
 */

@Getter
public enum SystemExceptionEnum {
    // === 成功 ===
    SUCCESS(0, "success"),
    // 1. 认证与账号相关 (1001~1009)
    INVALID_CREDENTIALS(1001, "用户名或密码错误"),
    ACCOUNT_DISABLED(1002, "该账号已被禁用，请联系管理员"),

    // === 权限相关 (1010~1019) ===
    ACCESS_DENIED(1010, "无权限执行该操作"),

    // === 资源与数据相关 (1020~1029) ===
    TICKET_NOT_FOUND(1020, "工单不存在"),

    // === 附件与文件相关 (1030~1039) ===
    UNSUPPORTED_ATTACHMENT_TYPE(1030, "仅支持上传图片类型文件"),
    ATTACHMENT_SIZE_EXCEEDED(1031, "图片大小不能超过 20MB"),
    ;
    private final String errMessage;
    private final int code;

    public String getErrMessage() {
        return errMessage;
    }

    SystemExceptionEnum(int code, String errmessage) {
        this.code = code;
        this.errMessage = errmessage;
    }
}