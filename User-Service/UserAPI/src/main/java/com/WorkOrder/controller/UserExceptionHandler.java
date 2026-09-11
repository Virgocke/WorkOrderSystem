package com.WorkOrder.controller;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.model.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/** 将用户服务的领域异常转换为统一 HTTP 响应。 */
@RestControllerAdvice
public class UserExceptionHandler {
    /**
     * 将资源不存在异常转换为 404 响应。
     *
     * @param exception 未找到资源异常
     * @return 统一错误响应
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Result<Void>(SystemExceptionEnum.RESOURCE_NOT_FOUND.getCode(),
                        exception.getMessage(),
                        null));
    }

    /**
     * 将业务参数异常转换为 400 响应。
     *
     * @param exception 非法参数异常
     * @return 统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new Result<Void>(SystemExceptionEnum.ILLEGAL_ARGUMENT.getCode(),
                        exception.getMessage(),
                        null));
    }
}
