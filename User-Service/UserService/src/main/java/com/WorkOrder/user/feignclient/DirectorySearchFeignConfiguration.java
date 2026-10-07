package com.WorkOrder.user.feignclient;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 仅用于目录搜索客户端，透传调用者身份，由 Search 服务再次校验管理员权限。
 */
public class DirectorySearchFeignConfiguration {
    /**
     * 仅转发当前请求的 Bearer 令牌，不在后台任务中伪造管理员身份。
     *
     * @return 请求Interceptor
     */
    @Bean
    public RequestInterceptor authorizationRequestInterceptor() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
                return;
            }
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }
}
