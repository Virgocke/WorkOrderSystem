package com.WorkOrder.controller;

import com.WorkOrder.security.handler.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 用户及处理人接口的统一异常控制器。 */
@RestControllerAdvice
public class UserExceptionHandler extends BaseExceptionHandler {
}
