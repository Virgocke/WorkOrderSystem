package com.WorkOrder.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

/**
 * @author Virgor
 * @date 2026年09月11日 00:29
 * @description Token configuration for OAuth2
 */
@Configuration
public class TokenConfig {
    @Value("${security.oauth2.jwt.signing-key}")
    private String signingKey; // JWT signing key

    @Autowired
    private JwtAccessTokenConverter accessTokenConverter;

    /**
     * 创建使用 JWT 本地解析访问令牌的存储组件。
     *
     * @return 使用 JWT 转换器解析令牌的令牌存储组件
     */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(accessTokenConverter);
    }

    /**
     * 创建并配置 JWT 访问令牌转换器。
     *
     * @return 配置完成的 JWT 访问令牌转换器
     */
    @Bean
    public JwtAccessTokenConverter accessTokenConverter(){
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setSigningKey(signingKey);
        return converter;
    }
}
