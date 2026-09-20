package com.WorkOrder.dashboard.security;

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

/** Search 服务的 OAuth2 资源服务器配置，在本地解析并验证 Bearer JWT。 */
@Configuration
@RequiredArgsConstructor
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
@SuppressWarnings("deprecation")
public class OAuth2ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    /** 用于输出统一 JSON 错误响应的序列化组件。 */
    private final ObjectMapper objectMapper;

    /** OAuth2 JWT 对称签名密钥。 */
    @Value("${security.oauth2.jwt.signing-key}")
    private String signingKey;

    /**
     * 创建负责 JWT 本地验签的访问令牌转换器。
     *
     * @return JWT 访问令牌转换器
     */
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setSigningKey(signingKey);
        return converter;
    }

    /**
     * 创建基于 JWT 的令牌存储器。
     *
     * @return JWT 令牌存储器
     */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessTokenConverter());
    }

    /**
     * 配置 Search 资源标识、令牌存储器和认证授权失败响应。
     *
     * @param resources OAuth2 资源服务器配置器
     */
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources.resourceId("Search")
                .tokenStore(tokenStore())
                .authenticationEntryPoint((request, response, exception) ->
                        writeError(response, HttpStatus.UNAUTHORIZED,
                                SystemExceptionEnum.ACCOUNT_OFFLINE))
                .accessDeniedHandler((request, response, exception) ->
                        writeError(response, HttpStatus.FORBIDDEN,
                                SystemExceptionEnum.ACCESS_DENIED));
    }

    /**
     * 要求所有仪表盘请求携带有效的 Bearer JWT。
     *
     * @param http HTTP 安全配置器
     * @throws Exception 安全规则配置失败时抛出
     */
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.requestMatchers().antMatchers("/dashboard/**", "/handler/**","/reports/**","/ratings/**")
                .and()
                .authorizeRequests()
                .anyRequest()
                .authenticated();
    }

    /**
     * 将认证或授权错误按统一响应结构写入客户端。
     *
     * @param response HTTP 响应
     * @param status HTTP 状态码
     * @param error 系统错误枚举
     * @throws IOException 写入响应失败时抛出
     */
    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            SystemExceptionEnum error) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Result.error(error));
    }
}
