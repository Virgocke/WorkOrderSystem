package com.WorkOrder.notification.service;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 创建与重试邮件任务共用的单个邮箱快照校验。
 */
public final class EmailRecipientValidator {
    /**
     * 工具类禁止实例化。
     */
    private EmailRecipientValidator() {
    }

    /**
     * 只接受标准邮箱地址，拒绝地址列表、显示名、换行及空快照。
     *
     * @param recipient 待检查的原收件地址快照，可为空
     * @return 单个地址通过格式、长度及换行检查时为 true；空值、列表或显示名地址为 false
     */
    public static boolean isValid(String recipient) {
        if (recipient == null || recipient.isEmpty() || recipient.length() > 254
                || recipient.contains("\r") || recipient.contains("\n")) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(recipient, true);
            address.validate();
            return recipient.equals(address.getAddress()) && recipient.contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }
}
