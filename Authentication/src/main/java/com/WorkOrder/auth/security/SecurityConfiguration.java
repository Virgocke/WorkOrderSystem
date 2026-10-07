package com.WorkOrder.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description OAuth2 密码模式使用的用户认证配置。
 */
@Configuration
@EnableWebSecurity
@SuppressWarnings("deprecation")
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    private final UserDetailsService userDetailsService;

    /**
     * 创建 Web 安全配置并注入数据库用户查询服务。
     *
     * @param userDetailsService 数据库用户查询服务
     */
    public SecurityConfiguration(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * 提供 BCrypt 密码编码器，数据库接入时保存的密码也必须使用该编码器加密。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 将用户查询服务和密码编码器绑定到 OAuth2 的密码授权流程。
     *
     * @param auth Spring Security 认证管理器构建器
     * @throws Exception 配置认证提供者失败时抛出
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
    }

    /**
     * 暴露认证管理器给 OAuth2 授权服务器，用于校验 password grant 中的用户名和密码。
     *
     * @return 认证管理器
     * @throws Exception 创建认证管理器失败时抛出
     */
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    /**
     * 放行 OAuth2 标准端点，其余 Web 请求由资源服务器配置按资源路径保护。
     *
     * @param http HTTP 安全配置器
     * @throws Exception 配置安全规则失败时抛出
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .antMatchers("/oauth/**", "/actuator/health").permitAll()
                .anyRequest().permitAll();
    }
}
