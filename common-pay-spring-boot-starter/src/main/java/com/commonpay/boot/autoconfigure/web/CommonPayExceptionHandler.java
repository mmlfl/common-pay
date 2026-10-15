package com.commonpay.boot.autoconfigure.web;

import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.common.R;
import com.commonpay.trade.exception.BaseException;
import com.commonpay.trade.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 统一异常 → {@link R}。约定：HTTP 始终 200，前端只认业务码 {@code R.code}。
 * <p>
 * 由 starter 自动注册；宿主可设 {@code common-pay.web.exception-handler=false} 关闭。
 */
@RestControllerAdvice
public class CommonPayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CommonPayExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        if (e.getCause() != null) {
            log.error("业务操作未完成 uri={} code={}", request.getRequestURI(), e.getCode(), e);
        } else {
            log.debug("业务拒绝 uri={} code={} message={}", request.getRequestURI(), e.getCode(), e.getMessage());
        }
        return R.fail(e.getCode(), e.clientMessage());
    }

    @ExceptionHandler(BaseException.class)
    public R<Void> handleBase(BaseException e, HttpServletRequest request) {
        log.error("系统操作未确认 uri={} code={}", request.getRequestURI(), e.getCode(), e);
        // 避免把库/渠道细节直接回给前端
        return R.fail(e.getCode(), e.publicMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public R<Void> handleValid(Exception e) {
        String message = ErrorCode.INVALID_ARGUMENT.getMessage();
        if (e instanceof BindException binding && binding.getBindingResult().getFieldError() != null) {
            String fieldMsg = binding.getBindingResult().getFieldError().getDefaultMessage();
            if (fieldMsg != null && !fieldMsg.isBlank()) {
                message = fieldMsg;
            }
        } else if (e instanceof IllegalArgumentException && e.getMessage() != null && !e.getMessage().isBlank()) {
            message = e.getMessage();
        }
        return R.fail(ErrorCode.INVALID_ARGUMENT.getCode(), message);
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public R<Void> handleRedis(Exception e, HttpServletRequest request) {
        log.error("Redis 不可用 uri={}", request.getRequestURI(), e);
        return R.fail(ErrorCode.REDIS_UNAVAILABLE.getCode(), ErrorCode.REDIS_UNAVAILABLE.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public R<Void> handleTodo(Exception e, HttpServletRequest request) {
        log.error("功能未实现 uri={}", request.getRequestURI(), e);
        return R.fail(ErrorCode.NOT_IMPLEMENTED.getCode(), ErrorCode.NOT_IMPLEMENTED.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("请求处理失败 uri={}", request.getRequestURI(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
