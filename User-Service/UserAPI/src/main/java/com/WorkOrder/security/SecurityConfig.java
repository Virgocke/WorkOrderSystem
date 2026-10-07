package com.WorkOrder.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 配置用户服务的密码编码器与本地 JWT 资源服务器认证。
 */
@Configuration
public class SecurityConfig {

    /**
     * 创建账号密码的 BCrypt 编码器。
     *
     * @return 用于密码编码和匹配的 BCrypt 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}