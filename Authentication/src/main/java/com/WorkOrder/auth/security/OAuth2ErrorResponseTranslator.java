package com.WorkOrder.auth.security;

import com.WorkOrder.enums.SystemExceptionEnum;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.provider.error.DefaultWebResponseExceptionTranslator;
import org.springframework.security.oauth2.provider.error.WebResponseExceptionTranslator;

import java.util.Locale;

/** 将 OAuth2 登录失败转换为前端可直接展示且不会泄露账号信息的提示。 */
public class OAuth2ErrorResponseTranslator implements WebResponseExceptionTranslator<OAuth2Exception> {
    private final DefaultWebResponseExceptionTranslator delegate = new DefaultWebResponseExceptionTranslator();

    /**
     * 保留标准 OAuth2 错误结构，并将 password grant 失败信息转换为统一中文提示。
     *
     * @param exception OAuth2 端点抛出的异常
     * @return 带标准 OAuth2 错误体的 HTTP 响应
     * @throws Exception 默认转换器无法处理异常时继续向上抛出
     */
    @Override
    public ResponseEntity<OAuth2Exception> translate(Exception exception) throws Exception {
        ResponseEntity<OAuth2Exception> response = delegate.translate(exception);
        OAuth2Exception body = response.getBody();
        if (body == null || !OAuth2Exception.INVALID_GRANT.equals(body.getOAuth2ErrorCode())) {
            return response;
        }

        String originalMessage = body.getMessage() == null ? "" : body.getMessage();
        String normalizedMessage = originalMessage.toLowerCase(Locale.ROOT);
        String message = normalizedMessage.contains("disabled") || originalMessage.contains("禁用")
                ? SystemExceptionEnum.ACCOUNT_DISABLED.getErrMessage()
                : SystemExceptionEnum.INVALID_CREDENTIALS.getErrMessage();
        InvalidGrantException translated = new InvalidGrantException(message);
        return new ResponseEntity<>(translated, response.getHeaders(), response.getStatusCode());
    }
}
