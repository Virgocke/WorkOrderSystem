package com.WorkOrder.ticket.controller;

import com.WorkOrder.security.handler.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 工单及工单分类接口的统一异常控制器。
 */
@RestControllerAdvice
public class TicketExceptionHandler extends BaseExceptionHandler {
}
