package com.WorkOrder.auth.util;

import java.security.SecureRandom;

/**
 * @author Virgor
 * @date 2026年09月12日 00:24
 * @description 生成6位验证码
 */
public class CodeUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成6位验证码
     * @return 6位验证码
     */
    public static String generate6DigitCode() {
        int num = RANDOM.nextInt(1_000_000);
        return String.format("%06d", num);
    }
}
