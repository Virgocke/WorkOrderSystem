package com.WorkOrder.auth.handler;

import com.WorkOrder.security.handler.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Virgor
 * @date 2026年09月10日 01:39
 * @description 认证业务接口的统一异常控制器。
 * 仅处理认证业务控制器，OAuth2令牌端点继续使用标准协议的异常转换器。
 */
@RestControllerAdvice(basePackages = "com.WorkOrder.auth.controller")
public class AuthExceptionHandler extends BaseExceptionHandler {
}
