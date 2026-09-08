package com.WorkOrder.auth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;

/** 让认证服务自身也以资源服务器方式校验 /api 下的 Bearer JWT。 */
@Configuration
@EnableResourceServer
@SuppressWarnings("deprecation")
public class OAuth2ResourceServerConfiguration extends ResourceServerConfigurerAdapter {
    private final TokenStore tokenStore;

    /**
     * 注入用于解析 JWT 的令牌存储器。
     *
     * @param tokenStore JWT 令牌存储器
     */
    public OAuth2ResourceServerConfiguration(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /**
     * 声明资源标识及令牌存储方式。
     *
     * @param resources 资源服务器配置器
     */
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources.resourceId("authentication-service").tokenStore(tokenStore);
    }

    /**
     * 保护认证服务自己的业务资源接口，不影响 OAuth2 标准端点。
     *
     * @param http HTTP 安全配置器
     * @throws Exception 配置资源权限失败时抛出
     */
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.requestMatchers().antMatchers("/api/**")
                .and().authorizeRequests().anyRequest().authenticated();
    }
}
