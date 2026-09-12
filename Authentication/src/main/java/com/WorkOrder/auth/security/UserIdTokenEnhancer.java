package com.WorkOrder.auth.security;

import com.WorkOrder.auth.mapper.UsersMapper;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.user.model.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 给 JWT 增加当前登录用户的数据库主键。
 */
@Component
@RequiredArgsConstructor
public class UserIdTokenEnhancer implements TokenEnhancer {

    public static final String USER_ID_CLAIM = "user_id";

    private final UsersMapper usersMapper;

    @Override
    public OAuth2AccessToken enhance(
            OAuth2AccessToken accessToken,
            OAuth2Authentication authentication) {

        /**
         * authentication.getName() 是当前登录用户名。
         * 这里只在登录签发 Token 或刷新 Token 时查询数据库。
         */
        String username = authentication.getName();

        Users user = usersMapper.findByUsername(username);
        if (user == null) {
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }

        /**
         * 保留其他 TokenEnhancer 已经加入的字段。
         */
        Map<String, Object> additionalInformation =
                new HashMap<>(accessToken.getAdditionalInformation());

        additionalInformation.put(USER_ID_CLAIM, user.getId());

        ((DefaultOAuth2AccessToken) accessToken)
                .setAdditionalInformation(additionalInformation);

        return accessToken;
    }
}