package com.WorkOrder.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 从认证详情中读取客户端 IP。
 */
public final class ClientIpUtils {

    /**
     * 工具类禁止实例化。
     */
    private ClientIpUtils() {
    }

    /**
     * 支持 Web 和 OAuth2 认证详情；没有可用详情时返回空字符串。
     *
     * @param authentication 携带 Web 或 OAuth2 请求详情的认证对象，可为空
     * @return 认证详情中的 remoteAddress；对象为空或详情类型不支持时返回空字符串，不解析代理转发头
     */
    public static String getClientIp(Authentication authentication) {
        if (authentication == null) {
            return "";
        }

        Object details = authentication.getDetails();
        if (details instanceof WebAuthenticationDetails) {
            return ((WebAuthenticationDetails) details).getRemoteAddress();
        }
        if (details instanceof OAuth2AuthenticationDetails) {
            return ((OAuth2AuthenticationDetails) details).getRemoteAddress();
        }
        return "";
    }
}
