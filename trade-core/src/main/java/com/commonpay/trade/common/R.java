package com.commonpay.trade.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * common-pay 统一接口返回体。宿主项目（如 ticket-demo）直接使用本类，勿再自建一套。
 * 异常由 starter 的 {@code CommonPayExceptionHandler} 转成 {@code R.fail(code, message)}（HTTP 200）。
 * <pre>
 * 成功：{@code return R.ok(data);}
 * 失败：{@code return R.fail(ErrorCode.STOCK_INSUFFICIENT.getCode(), "...");}
 * </pre>
 */
@Data
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功，非 0 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public static final int CODE_OK = 0;
    public static final int CODE_FAIL = 1;

    public boolean isSuccess() {
        return code == CODE_OK;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        return ok("ok", data);
    }

    public static <T> R<T> ok(String message, T data) {
        R<T> r = new R<>();
        r.code = CODE_OK;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(CODE_FAIL, message);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }
}
