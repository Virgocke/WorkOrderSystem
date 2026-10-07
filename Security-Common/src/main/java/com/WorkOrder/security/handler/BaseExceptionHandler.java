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
 * @author Virgor
 * @date 2026年10月07日
 * @description HTTP业务接口的公共异常处理规则，由各服务的异常控制器继承。
 */
@Slf4j
public abstract class BaseExceptionHandler {

    /**
     * 按业务错误类型返回HTTP状态码，同时保留原业务错误码。
     *
     * @param exception 包含业务错误枚举或自定义提示的系统异常
     * @return 按业务错误映射 HTTP 状态码并保留统一业务码的响应
     */
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
            case CONFIGURATION_VERSION_CONFLICT:
            case TICKET_NO_EXHAUSTED:
            case TICKET_SEARCH_JOB_CONFLICT:
            case EMAIL_RETRY_CONFLICT:
                // 运维任务或人工重发冲突使用 409，提示调用方读取最新状态。
                status = HttpStatus.CONFLICT;
                break;
            case TICKET_SEARCH_NOT_READY:
                // 暂时不能提供关键词搜索，保留业务码供搜索专属 Feign 解码器跨服务识别。
                status = HttpStatus.SERVICE_UNAVAILABLE;
                break;
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

    /**
     * 缺少参数、参数类型错误或无法读取JSON时返回统一参数错误。
     *
     * @param exception 缺失参数、参数类型不匹配或 JSON 无法解析的请求异常
     * @return HTTP 400 及统一非法参数业务错误
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT));
    }

    /**
     * 保留业务代码提供的参数错误提示。
     *
     * @param exception 业务代码抛出的参数异常，可能包含可展示的提示
     * @return HTTP 400 及非法参数业务码；优先保留非空异常提示
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> illegalArgument(IllegalArgumentException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage();
        return ResponseEntity.badRequest()
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
    }

    /**
     * 处理请求体校验、查询参数绑定和校验失败，返回DTO定义的校验提示。
     *
     * @param exception 请求参数绑定或 DTO 校验失败的异常
     * @return HTTP 400 及首条 DTO 校验提示
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> validationFailed(BindException exception) {
        return validationError(exception.getBindingResult());
    }

    /**
     * 处理JSON请求体的DTO校验失败。
     *
     * @param exception JSON 请求体 DTO 未通过 Bean Validation 的异常
     * @return HTTP 400 及首条请求体校验提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> invalidRequestBody(MethodArgumentNotValidException exception) {
        return validationError(exception.getBindingResult());
    }

    /**
     * 处理参数校验失败，返回第一个校验错误。
     *
     * @param bindingResult 包含 DTO 绑定错误和校验提示的结果
     * @return HTTP 400 及 BindingResult 原顺序中的首条校验提示；无有效提示时使用默认非法参数消息
     */
    private ResponseEntity<Result<Void>> validationError(BindingResult bindingResult) {
        ObjectError error = bindingResult.getAllErrors().stream().findFirst().orElse(null);
        String message = error != null && StringUtils.hasText(error.getDefaultMessage())
                ? error.getDefaultMessage() : SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage();
        return ResponseEntity.badRequest()
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
    }

    /**
     * 处理方法参数约束校验失败。
     *
     * @param exception 方法参数约束违反异常，可能包含多条提示
     * @return HTTP 400 及按提示文本排序后的首条约束错误
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> constraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage()).sorted().findFirst()
                .orElse(SystemExceptionEnum.ILLEGAL_ARGUMENT.getErrMessage());
        return ResponseEntity.badRequest()
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, message));
    }

    /**
     * 处理用户服务等模块的资源不存在异常。
     *
     * @param exception 查询的资源不存在异常
     * @return HTTP 404 及资源不存在业务码，保留非空异常提示
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> notFound(NoSuchElementException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : SystemExceptionEnum.RESOURCE_NOT_FOUND.getErrMessage();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(SystemExceptionEnum.RESOURCE_NOT_FOUND, message));
    }

    /**
     * 控制器的方法权限校验失败返回403。
     *
     * @param exception Spring Security 方法鉴权拒绝访问的异常
     * @return HTTP 403 及无权限业务错误
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(SystemExceptionEnum.ACCESS_DENIED));
    }

    /**
     * 保留不支持请求方法的405状态，避免被兜底处理转换为500。
     *
     * @param exception 请求方法不受目标接口支持的异常
     * @return HTTP 405、允许的方法头及统一业务错误
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, "不支持的请求方法"));
    }

    /**
     * 请求内容类型不受支持时保留415状态。
     *
     * @param exception 请求 Content-Type 不受接口支持的异常
     * @return HTTP 415 及请求内容类型不受支持的提示
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, "不支持的请求内容类型"));
    }

    /**
     * 响应内容类型无法满足客户端要求时保留406状态。
     *
     * @param exception 请求 Accept 类型无法满足的异常
     * @return HTTP 406 及响应内容类型不受支持的提示
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> notAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(Result.error(SystemExceptionEnum.ILLEGAL_ARGUMENT, "不支持的响应内容类型"));
    }

    /**
     * 处理业务接口中抛出的认证异常。
     *
     * @param exception 凭据错误、未登录或账号状态异常
     * @return 账号状态异常返回 HTTP 403；其他认证异常返回 HTTP 401 及对应业务码
     */
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

    /**
     * 未预期异常记录完整日志，响应只返回统一服务错误提示。
     *
     * @param exception 未被其他处理器识别的服务端异常
     * @return HTTP 500 及统一服务错误，不向客户端返回异常堆栈
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> internalServerError(Exception exception) {
        log.error("业务接口发生未预期异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SystemExceptionEnum.INTERNAL_SERVER_ERROR));
    }
}
