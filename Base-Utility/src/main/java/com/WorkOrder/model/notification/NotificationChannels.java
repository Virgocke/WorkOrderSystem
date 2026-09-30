package com.WorkOrder.model.notification;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

/** 工单通知渠道；站内信固定开启，邮件由管理员控制，不提供短信投递。 */
@Getter
public final class NotificationChannels {
    /** 未保存配置时沿用页面默认值。 */
    public static final String DEFAULT_JSON = "{\"internal\":true,\"email\":true}";
    /** 是否为新消费的工单事件创建邮件任务。 */
    private final boolean email;

    private NotificationChannels(boolean email) {
        this.email = email;
    }

    /** 校验新提交的完整配置；拒绝关闭站内信、非布尔值及额外字段。 */
    public static NotificationChannels fromJson(JsonNode value) {
        return parse(value, false);
    }

    /** 兼容旧库中的布尔 sms 字段，忽略其值；下一次保存时移除该字段。 */
    public static NotificationChannels fromStoredJson(JsonNode value) {
        return parse(value, true);
    }

    /** 统一保存和读取约束，存量兼容只限于已废弃的短信字段。 */
    private static NotificationChannels parse(JsonNode value, boolean stored) {
        boolean legacy = stored && value != null && value.has("sms") && value.get("sms").isBoolean();
        if (value == null || !value.isObject() || value.size() != (legacy ? 3 : 2)
                || !value.has("internal") || !value.get("internal").isBoolean()
                || !value.get("internal").booleanValue()
                || !value.has("email") || !value.get("email").isBoolean()) {
            throw new IllegalArgumentException("通知渠道须为internal=true和email布尔值，不支持短信渠道");
        }
        return new NotificationChannels(value.get("email").booleanValue());
    }
}
