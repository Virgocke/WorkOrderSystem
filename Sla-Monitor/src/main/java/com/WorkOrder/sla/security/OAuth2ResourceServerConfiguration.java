package com.WorkOrder.sla.security;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.model.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 通知服务的 OAuth2 资源服务器配置，在本地解析并验证 Bearer JWT。 */
@Configuration
@RequiredArgsConstructor
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
@SuppressWarnings("deprecation")
public class OAuth2ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    private final ObjectMapper objectMapper;

    @Value("${security.oauth2.jwt.signing-key}")
    private String signingKey;

    /** 创建 JWT 本地验签转换器。 */
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setSigningKey(signingKey);
        return converter;
    }

    /** 创建仅用于 JWT 本地解析的令牌存储器。 */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessTokenConverter());
    }

    /** 声明通知服务资源标识和认证失败响应。 */
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources.resourceId("Notification")
                .tokenStore(tokenStore())
                .authenticationEntryPoint((request, response, exception) ->
                        writeError(response, HttpStatus.UNAUTHORIZED, SystemExceptionEnum.ACCOUNT_OFFLINE))
                .accessDeniedHandler((request, response, exception) ->
                        writeError(response, HttpStatus.FORBIDDEN, SystemExceptionEnum.ACCESS_DENIED));
    }

    /** 保护通知接口，调用方必须携带 OAuth2 Bearer JWT。 */
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.requestMatchers().antMatchers("/sla/**")
                .and()
                .authorizeRequests()
                .anyRequest()
                .authenticated();
    }

    /**
     * 向客户端写入错误响应。
     * @param response 响应对象
     * @param status 状态码
     * @param error 错误信息
     * @throws IOException
     */
    private void writeError(HttpServletResponse response, HttpStatus status,
                            SystemExceptionEnum error) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Result.error(error));
    }
}
