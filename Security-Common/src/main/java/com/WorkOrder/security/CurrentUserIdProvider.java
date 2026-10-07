package com.WorkOrder.security;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 从已经过 Spring Security 校验的 JWT 中读取当前用户 ID。
 *
 * 业务模块只需提供自己的 {@link TokenStore}，无需访问用户数据库。
 */
@Component
public class CurrentUserIdProvider {

    private static final String USER_ID_CLAIM = "user_id";

    private final TokenStore tokenStore;

    /**
     * 注入本服务的 JWT 令牌解析组件。
     *
     * @param tokenStore 本服务用于解析已认证 JWT 及其附加声明的令牌存储组件
     */
    public CurrentUserIdProvider(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /**
     * 获取当前登录用户的数据库主键。
     *
     * @param authentication 已经通过 Spring Security 认证且包含 OAuth2 令牌详情的登录信息
     * @return JWT 的 user_id 声明对应的用户主键；本方法不查询用户资料或账号启用状态
     */
    public Long get(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SystemException(SystemExceptionEnum.ACCOUNT_OFFLINE);
        }

        Object authenticationDetails = authentication.getDetails();
        if (!(authenticationDetails instanceof OAuth2AuthenticationDetails)) {
            throw new SystemException("当前请求中不存在有效的 OAuth2 Token");
        }

        OAuth2AuthenticationDetails oauth2Details =
                (OAuth2AuthenticationDetails) authenticationDetails;

        String tokenValue = oauth2Details.getTokenValue();
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            throw new SystemException("当前请求中不存在有效的访问令牌");
        }

        OAuth2AccessToken accessToken;
        try {
            accessToken = tokenStore.readAccessToken(tokenValue);
        } catch (InvalidTokenException exception) {
            throw new InsufficientAuthenticationException("访问令牌无效", exception);
        }

        if (accessToken == null) {
            throw new SystemException("访问令牌无效");
        }

        Map<String, Object> claims = accessToken.getAdditionalInformation();
        Object userIdValue = claims.get(USER_ID_CLAIM);

        if (userIdValue == null) {
            throw new SystemException("访问令牌中缺少 user_id");
        }

        if (userIdValue instanceof Number) {
            return ((Number) userIdValue).longValue();
        }

        try {
            return Long.valueOf(userIdValue.toString());
        } catch (NumberFormatException exception) {
            throw new InsufficientAuthenticationException(
                    "访问令牌中的 user_id 格式错误",
                    exception
            );
        }
    }
}
