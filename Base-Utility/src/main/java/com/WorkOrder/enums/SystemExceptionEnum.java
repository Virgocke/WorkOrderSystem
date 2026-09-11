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
    ACCOUNT_OFFLINE(1003, "账号未登录"),
    ACCOUNT_HAS_BEEN_CREATED(1004, "账号已存在"),
    REGISTER_FAILED(1005, "注册失败"),
    CODE_IS_EXPIRED(1006, "验证码已过期"),
    USER_NOT_FOUND(1007, "用户不存在"),
    CODE_ERROR(1008, "验证码错误"),

    // === 权限相关 (1020~1029) ===
    ACCESS_DENIED(1020, "无权限执行该操作"),

    // === 资源与数据相关 (1040~1049) ===
    TICKET_NOT_FOUND(1040, "工单不存在"),

    // === 附件与文件相关 (1060~1069) ===
    UNSUPPORTED_ATTACHMENT_TYPE(1060, "仅支持上传图片类型文件"),
    ATTACHMENT_SIZE_EXCEEDED(1061, "图片大小不能超过 20MB"),
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