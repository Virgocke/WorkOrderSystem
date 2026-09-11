package com.WorkOrder.auth.service;

/**
 * @author Virgor
 * @date 2026年09月12日 00:32
 * @description
 */
public interface SendCodeService {
    void sendCode(String email);

    boolean verifyCode(String email, String code);
}
