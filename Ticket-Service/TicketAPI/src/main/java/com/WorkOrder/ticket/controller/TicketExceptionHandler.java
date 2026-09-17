package com.WorkOrder.ticket.controller;

import com.WorkOrder.security.handler.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 工单及工单分类接口的统一异常控制器。 */
@RestControllerAdvice
public class TicketExceptionHandler extends BaseExceptionHandler {
}
