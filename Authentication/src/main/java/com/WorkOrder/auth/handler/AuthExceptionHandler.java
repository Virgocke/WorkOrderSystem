package com.WorkOrder.auth.handler;

import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Virgor
 * @date 2026年09月10日 01:39
 * @description
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Result<Void>> handleSystemException(SystemException e){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(e.getError()));
    }
}
