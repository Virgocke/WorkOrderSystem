package com.WorkOrder.security.handler;

import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolationException;
import java.util.NoSuchElementException;

/**
 * @description HTTP业务接口的公共异常处理规则，由各服务的异常控制器继承。
 */
@Slf4j
public abstract class BaseExceptionHandler {

    /** 按业务错误类型返回HTTP状态码，同时保留原业务错误码。 */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Result<Void>> handleSystemException(SystemException exception) {
        SystemExceptionEnum error = exception.getError();
        if (error == null) {
            String message = StringUtils.hasText(exception.getErrMessage())
                    ? exception.getErrMessage() : SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage();
            return ResponseEntity.badRequest()
                    .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
        }

        HttpStatus status;
        switch (error) {
            case TICKET_NOT_FOUND:
            case RESOURCE_NOT_FOUND:
            case USER_NOT_FOUND:
                status = HttpStatus.NOT_FOUND;
                break;
            case ACCESS_DENIED:
            case ACCOUNT_DISABLED:
                status = HttpStatus.FORBIDDEN;
                break;
            case ACCOUNT_OFFLINE:
            case INVALID_CREDENTIALS:
                status = HttpStatus.UNAUTHORIZED;
                break;
            case INTERNAL_SERVER_ERROR:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
            default:
                status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(Result.error(error));
    }

    /** 缺少参数、参数类型错误或无法读取JSON时返回统一参数错误。 */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT));
    }

    /** 保留业务代码提供的参数错误提示。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> illegalArgument(IllegalArgumentException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage();
        return ResponseEntity.badRequest()
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
    }

    /** 处理请求体校验、查询参数绑定和校验失败，返回DTO定义的校验提示。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> validationFailed(BindException exception) {
        return validationError(exception.getBindingResult());
    }

    /** 处理JSON请求体的DTO校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> invalidRequestBody(MethodArgumentNotValidException exception) {
        return validationError(exception.getBindingResult());
    }

    /**
     * 处理参数校验失败，返回第一个校验错误。
     * @param bindingResult 校验结果
     * @return 校验错误响应
     */
    private ResponseEntity<Result<Void>> validationError(BindingResult bindingResult) {
        ObjectError error = bindingResult.getAllErrors().stream().findFirst().orElse(null);
        String message = error != null && StringUtils.hasText(error.getDefaultMessage())
                ? error.getDefaultMessage() : SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage();
        return ResponseEntity.badRequest()
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
    }

    /** 处理方法参数约束校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> constraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage()).sorted().findFirst()
                .orElse(SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage());
        return ResponseEntity.badRequest()
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
    }

    /** 处理用户服务等模块的资源不存在异常。 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> notFound(NoSuchElementException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : SystemExceptionEnum.RESOURCE_NOT_FOUND.getErrMessage();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(SystemExceptionEnum.RESOURCE_NOT_FOUND, message));
    }

    /** 控制器的方法权限校验失败返回403。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(SystemExceptionEnum.ACCESS_DENIED));
    }

    /** 保留不支持请求方法的405状态，避免被兜底处理转换为500。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, "不支持的请求方法"));
    }

    /** 请求内容类型不受支持时保留415状态。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, "不支持的请求内容类型"));
    }

    /** 响应内容类型无法满足客户端要求时保留406状态。 */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> notAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, "不支持的响应内容类型"));
    }

    /** 处理业务接口中抛出的认证异常。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> unauthorized(AuthenticationException exception) {
        if (exception instanceof AccountStatusException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(SystemExceptionEnum.ACCOUNT_DISABLED));
        }
        SystemExceptionEnum error = exception instanceof BadCredentialsException
                ? SystemExceptionEnum.INVALID_CREDENTIALS : SystemExceptionEnum.ACCOUNT_OFFLINE;
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(error));
    }

    /** 未预期异常记录完整日志，响应只返回统一服务错误提示。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> internalServerError(Exception exception) {
        log.error("业务接口发生未预期异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SystemExceptionEnum.INTERNAL_SERVER_ERROR));
    }
}
