package com.WorkOrder.controller;

import com.WorkOrder.security.handler.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 用户及处理人接口的统一异常控制器。
 */
@RestControllerAdvice
public class UserExceptionHandler extends BaseExceptionHandler {
}
