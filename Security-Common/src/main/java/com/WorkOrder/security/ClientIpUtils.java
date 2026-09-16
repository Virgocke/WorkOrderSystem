package com.WorkOrder.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * 从认证详情中读取客户端 IP。
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    /**
     * 支持 Web 和 OAuth2 认证详情；没有可用详情时返回空字符串。
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
