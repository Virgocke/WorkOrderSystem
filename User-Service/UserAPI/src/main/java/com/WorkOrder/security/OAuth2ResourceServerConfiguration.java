package com.WorkOrder.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

/** 用户服务的 OAuth2 资源服务器配置，在本地解析并验证 Bearer JWT。 */
@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
@SuppressWarnings("deprecation")
public class OAuth2ResourceServerConfiguration extends ResourceServerConfigurerAdapter {
    @Value("${security.oauth2.jwt.signing-key}")
    private String signingKey;

    /**
     * 创建 JWT 验签转换器。生产环境应切换到非对称密钥，仅在资源服务保存公钥。
     *
     * @return JWT 转换器
     */
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setSigningKey(signingKey);
        return converter;
    }

    /**
     * 创建仅用于 JWT 本地解析的令牌存储器。
     *
     * @return JWT 令牌存储器
     */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessTokenConverter());
    }

    /**
     * 声明用户服务资源标识和本地 JWT 令牌存储方式。
     *
     * @param resources 资源服务器配置器
     */
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources.resourceId("userAndHandler-service").tokenStore(tokenStore());
    }

    /**
     * 保护用户和处理人资料接口，调用方必须携带 OAuth2 Bearer JWT。
     *
     * @param http HTTP 安全配置器
     * @throws Exception 配置资源权限失败时抛出
     */
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.requestMatchers().antMatchers("/users/**", "/handlers/**", "/departments/**")
                .and()
                .authorizeRequests()
                .anyRequest()
                .authenticated();
    }
}
