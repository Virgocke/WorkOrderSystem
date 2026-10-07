package com.WorkOrder.auth.service;

/**
 * @author Virgor
 * @date 2026年09月12日 00:32
 * @description 邮箱验证码的发送与校验服务。
 */
public interface SendCodeService {
    /**
     * 生成并发送邮箱验证码，保存验证码的有效期并限制发送频率。
     *
     * @param email 已注册账号的邮箱地址
     */
    void sendCode(String email);

    /**
     * 校验邮箱验证码，并在匹配成功后删除验证码。
     *
     * @param email 验证码对应的邮箱地址
     * @param code 用户提交的邮箱验证码
     * @return 验证码匹配且已消费时为 true，否则为 false
     */
    boolean verifyCode(String email, String code);
}
