package com.commonpay.trade.exception;

import com.commonpay.trade.common.ErrorCode;

/**
 * 已确定的业务拒绝；message 必须可展示给用户。
 * 支持 {@link ErrorCode} 或裸 {@code code + message}。
 */
public class BusinessException extends BaseException {

    private static final long serialVersionUID = 1L;

    public BusinessException(ErrorCode code) {
        super(code);
    }

    public BusinessException(ErrorCode code, String message) {
        super(code, message);
    }

    public BusinessException(ErrorCode code, Throwable cause) {
        super(code, cause);
    }

    public BusinessException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public BusinessException(int code, String message) {
        super(code, message);
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * MQ / 定时任务是否应重试。有 {@link ErrorCode} 时看枚举；裸 code 的业务异常默认不重试。
     */
    public boolean isRetryable() {
        return getErrorCode() != null && getErrorCode().isRetryable();
    }
}
