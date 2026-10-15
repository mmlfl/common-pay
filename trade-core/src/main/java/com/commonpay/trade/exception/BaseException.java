package com.commonpay.trade.exception;

import com.commonpay.trade.common.ErrorCode;

import java.util.Objects;

/**
 * 通用异常基础：对外是 {@code code + message}，不依赖 HTTP。
 * {@link ErrorCode} 只是常见场景的 (code, message) 封装，也可直接传裸 code。
 */
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;
    /** 由 ErrorCode 构造时非空；裸 code 构造时可为 null。 */
    private final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public BaseException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    public BaseException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        this(Objects.requireNonNull(errorCode, "errorCode").getCode(),
                blankToDefault(message, errorCode.getMessage()),
                errorCode,
                cause);
    }

    public BaseException(int code, String message) {
        this(code, message, null, null);
    }

    public BaseException(int code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    private BaseException(int code, String message, ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = code;
        this.errorCode = errorCode;
    }

    private static String blankToDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }

    public int getCode() {
        return code;
    }

    /** 可能为 null（裸 code 构造时）。 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 给前端的提示：优先异常 message。 */
    public String clientMessage() {
        return getMessage();
    }

    /** 系统类异常对外公开文案：有 ErrorCode 用枚举文案，否则用 message。 */
    public String publicMessage() {
        return errorCode != null ? errorCode.getMessage() : getMessage();
    }
}
