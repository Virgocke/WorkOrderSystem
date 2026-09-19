package com.WorkOrder.ticket.feignclient;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 仅由分配记录的两个 Feign 客户端加载，向内部服务透传当前请求的 Bearer 令牌。 */
public class TicketFeignConfiguration {

    /**
     * 向内部服务透传当前请求的 Bearer 令牌。
     * @return 请求拦截器
     */
    @Bean
    public RequestInterceptor authorizationRequestInterceptor() {
        return template -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes)) {
                return;
            }
            String authorization = ((ServletRequestAttributes) attributes).getRequest()
                    .getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }
}
