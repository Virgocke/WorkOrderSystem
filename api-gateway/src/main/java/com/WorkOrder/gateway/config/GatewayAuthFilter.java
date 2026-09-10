package com.WorkOrder.gateway.config;

import com.WorkOrder.exception.SystemException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年09月11日 01:10
 * @description
 */
@Component
@Slf4j
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static List<String> whiteList = null;

    /**
     * 静态代码块，加载白名单配置
     */
    static {
        try(InputStream resourceAsStream = GatewayAuthFilter.class.getResourceAsStream("/security-whitelist.properties")){
            Properties properties = new Properties();
            properties.load(resourceAsStream);
            Set<String> strings = properties.stringPropertyNames();
            whiteList = new ArrayList<>(strings);
        } catch (Exception e) {
            log.error("加载/security-whitelist.properties出错:{}",e.getMessage());
            e.printStackTrace();
        }
    }

    @Autowired
    private TokenStore tokenStore;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestUrl = exchange.getRequest().getPath().value();
        AntPathMatcher pathMatcher = new AntPathMatcher();
        //白名单放行
        for (String url : whiteList){
            if (pathMatcher.match(url, requestUrl)) {
                //白名单放行
                return chain.filter(exchange);
            }
        }

        //检查token是否存在
        String token = getToken(exchange);
        if(StringUtils.isBlank(token)){
            return buildReturnMono("没有认证", exchange);
        }

        //判断是否是有效的token
        OAuth2AccessToken oAuth2AccessToken;
        try{
            oAuth2AccessToken = tokenStore.readAccessToken(token);
            boolean expired = oAuth2AccessToken.isExpired();
            if (expired) {
                return buildReturnMono("token已过期", exchange);
            }
            return chain.filter(exchange);
        } catch (InvalidTokenException e) {
            log.info("token无效: {}", token);
            return buildReturnMono("token无效", exchange);
        }
    }

    private Mono<Void> buildReturnMono(String error, ServerWebExchange exchange){
        ServerHttpResponse response = exchange.getResponse();
        String jsonString = JSON.toJSONString(new SystemException(error));
        byte[] bits = jsonString.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bits);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 从请求头中获取token
     * @param exchange
     * @return token字符串
     */
    private String getToken(ServerWebExchange exchange){
        String tokenStr = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.isBlank(tokenStr)) {
            return null;
        }
        String token = tokenStr.split(" ")[1];
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return token;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
