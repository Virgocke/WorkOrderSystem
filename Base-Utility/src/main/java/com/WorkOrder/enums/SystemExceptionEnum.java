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
    ABNORMAL_ACCOUNT(1009, "账号异常"),

    // === 权限相关 (1020~1029) ===
    ACCESS_DENIED(1020, "无权限执行该操作"),
    TICKET_STATUS_NOT_ALLOWED(1021, "工单状态不允许执行该操作"),
    TICKET_NOT_RESOLVED(1022, "工单未解决，不允许执行该操作"),
    APPLICATION_FAILED(1023, "申请失败"),

    // === 资源与数据相关 (1040~1049) ===
    TICKET_NOT_FOUND(1040, "工单不存在"),
    RESOURCE_NOT_FOUND(1041, "资源不存在"),
    ILLEGAL_ARGUMENT(1042, "非法参数"),
    CREATE_FAILED(1043, "创建失败"),
    TICKET_CREATE_FAILED(1044, "工单创建失败"),
    TICKET_ALREADY_ESCALATED(1045, "工单催办次数已上限，请联系管理员"),
    TICKET_RATING_HAS_BEEN_MADE(1046, "工单已评价，不允许重复评价"),
    TICKET_STATUS_UPDATE_ERROR(1047, "工单状态更新失败"),
    TICKET_TRANSFER_SAME_HANDLER(1048, "工单当前处理人不能是转办人"),
    TICKET_ESCALATED_LEVEL_MAX(1049, "工单升级级别已达上限，请联系管理员"),
    NOTIFICATION_READ_FAILED(1050, "通知已读失败"),
    ALERT_HANDLE_FAILED(1051, "告警处理失败，请刷新后重试"),
    ATTACHMENT_TIME_EXPIRED(1052, "临时附件保存时间已过，请重新上传"),
    ATTACHMENT_NOT_FOUND(1053, "附件不存在"),
    TICKET_NO_EXHAUSTED(1054, "当前工单编号序号已达上限"),

    // === 附件与文件相关 (1060~1069) ===
    UNSUPPORTED_ATTACHMENT_TYPE(1060, "仅支持上传图片类型文件"),
    ATTACHMENT_SIZE_EXCEEDED(1061, "图片大小不能超过 20MB"),
    ATTACHMENT_BIND_FAILED(1062, "附件不存在、已过期、已绑定或不属于当前用户"),

    // === 系统配置相关 (1080~1089) ===
    CONFIGURATION_VERSION_CONFLICT(1080, "配置已被其他管理员修改，请刷新后重试"),

    // === 工单搜索运维相关 (1090~1099) ===
    TICKET_SEARCH_NOT_READY(1090, "关键词搜索暂不可用，可清空关键词查看工单"),
    TICKET_SEARCH_JOB_CONFLICT(1091, "工单搜索任务状态已变化，请刷新后重试"),

    // === 服务内部错误 ===
    INTERNAL_SERVER_ERROR(5000, "系统内部错误，请稍后重试"),
    ;
    /** 返回给调用方的错误说明。 */
    private final String errMessage;
    /** 稳定的业务错误码。 */
    private final int code;

    /** @return 错误说明 */
    public String getErrMessage() {
        return errMessage;
    }

    /** 保存错误码与对应的错误说明。 */
    SystemExceptionEnum(int code, String errmessage) {
        this.code = code;
        this.errMessage = errmessage;
    }
}
