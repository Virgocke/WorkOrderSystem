package com.WorkOrder.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * @author Virgor
 * @date 2026年09月11日 00:39
 * @description 所有路径都不需要认证
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain webFilterChain(ServerHttpSecurity http){
        return http.authorizeExchange()
                .pathMatchers("/**").permitAll()
                .anyExchange().authenticated()
                .and().csrf().disable().build();
    }
}
