package com.commonpay.trade.redis;

import java.util.Objects;

/**
 * 通用扣减结果。成功看 {@link #isSuccess()} + {@link #getRemain()}；
 * 失败看 {@link #getErrorCode()}。
 */
public final class DecrResult {

    private final boolean success;
    private final long remain;
    private final RedisErrorCode errorCode;

    private DecrResult(boolean success, long remain, RedisErrorCode errorCode) {
        this.success = success;
        this.remain = remain;
        this.errorCode = errorCode;
    }

    public static DecrResult ok(long remainAfter) {
        return new DecrResult(true, remainAfter, null);
    }

    public static DecrResult fail(RedisErrorCode errorCode, long current) {
        Objects.requireNonNull(errorCode, "errorCode");
        return new DecrResult(false, current, errorCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public long getRemain() {
        return remain;
    }

    public RedisErrorCode getErrorCode() {
        return errorCode;
    }
}
