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
 * @description 网关认证过滤器
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

    /**
     * 放行白名单请求，其余请求验证访问令牌后继续执行网关过滤器链。
     *
     * @param exchange 当前网关请求与响应上下文
     * @param chain 后续网关过滤器链
     * @return 请求放行或写入认证失败响应的异步完成信号
     */
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

    /**
     * 写入 HTTP 401 的 JSON 认证失败响应。
     *
     * @param error 返回给调用方的认证错误提示
     * @param exchange 当前网关请求与响应上下文
     * @return 认证失败响应写入完成的异步信号
     */
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
     *
     * @param exchange 当前网关请求与响应上下文
     * @return Authorization 请求头中的访问令牌；请求头或令牌为空时为 null
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

    /**
     * 获取网关认证过滤器的执行顺序。
     *
     * @return 过滤器顺序值，当前为 0
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
