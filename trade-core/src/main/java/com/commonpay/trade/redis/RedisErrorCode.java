package com.commonpay.trade.redis;

/**
 * Redis 操作通用错误码。业务侧用枚举分支，后续锁、幂等等失败原因也往这里加。
 */
public enum RedisErrorCode {

    KEY_MISSING,
    UNKNOWN,
    INSUFFICIENT
}
