package com.WorkOrder.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

/**
 * OAuth2 授权服务器：负责签发带签名的 JWT 访问令牌与刷新令牌。
 * 当前保留 password grant 供内部前端联调；对第三方登录应切换到 authorization_code + PKCE。
 */
@Configuration
@EnableAuthorizationServer
@SuppressWarnings("deprecation")
public class OAuth2AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.oauth2.jwt.signing-key}")
    private String signingKey;
    @Value("${security.oauth2.client.id}")
    private String clientId;
    @Value("${security.oauth2.client.secret}")
    private String clientSecret;

    /**
     * 注入 OAuth2 所需的身份认证组件。
     *
     * @param authenticationManager 用户名密码认证管理器
     * @param userDetailsService 用户资料查询服务
     * @param passwordEncoder 客户端密钥编码器
     */
    public OAuth2AuthorizationServerConfiguration(AuthenticationManager authenticationManager,
                                                  UserDetailsService userDetailsService,
                                                  PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建 JWT 转换器，并使用配置的签名密钥对访问令牌签名。
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
     * 令牌仅以 JWT 形式保存，服务重启后不需要恢复服务端会话。
     *
     * @return JWT 令牌存储器
     */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessTokenConverter());
    }

    /**
     * 注册内部 Web 客户端及其授权范围。
     *
     * @param clients OAuth2 客户端配置器
     * @throws Exception 配置客户端失败时抛出
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
                .withClient(clientId)
                .secret(passwordEncoder.encode(clientSecret))
                .authorizedGrantTypes("password", "refresh_token")
                .scopes("work-order")
                .accessTokenValiditySeconds(30 * 60)
                .refreshTokenValiditySeconds(7 * 24 * 60 * 60);
    }

    /**
     * 绑定 OAuth2 标准端点、密码认证、JWT 转换器和刷新令牌能力。
     *
     * @param endpoints OAuth2 端点配置器
     * @throws Exception 配置端点失败时抛出
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints.authenticationManager(authenticationManager)
                .userDetailsService(userDetailsService)
                .tokenStore(tokenStore())
                .accessTokenConverter(jwtAccessTokenConverter())
                .tokenEnhancer(jwtAccessTokenConverter());
    }

    /**
     * 允许客户端以表单提交凭据，并仅向已认证客户端开放令牌校验端点。
     *
     * @param security OAuth2 端点安全配置器
     * @throws Exception 配置端点安全策略失败时抛出
     */
    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.tokenKeyAccess("permitAll()")
                .checkTokenAccess("isAuthenticated()")
                .allowFormAuthenticationForClients();
    }
}
