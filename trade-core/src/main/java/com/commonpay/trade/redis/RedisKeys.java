package com.commonpay.trade.redis;

/**
 * Redis key 的唯一来源。任何地方要用 key 都从这里取，不许自己拼字符串。
 * <p>不要在这里写 keyPrefix，{@link RedisUtil#fullKey} 会自动加。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 商品可售库存计数器 */
    public static String stock(Long productId) {
        return "stock:" + productId;
    }

    /** 下单幂等位。值存【上次的结果】，重复请求读出来原样回放 */
    public static String orderIdempotent(String requestId) {
        return "order:idem:" + requestId;
    }

    public static String reservationReturned(String attemptToken) { return "stock:reservation-returned:" + attemptToken; }

    /** 预扣义务索引（ZSET：score=预扣时间，member=requestId），供崩溃对账扫描。 */
    public static String reservationDebtIndex() {
        return "stock:reservation-debt";
    }

    /** 预扣义务详情：productId/quantity/attemptToken/createdAt。 */
    public static String reservationDebt(String requestId) {
        return "stock:reservation-debt:" + requestId;
    }

    public static String stockReleased(Long orderId) { return "stock:released:" + orderId; }

    /** 本次库存预热已经包含的回库序号。 */
    public static String stockReleaseBaseline(Long productId) { return "stock:release-baseline:" + productId; }

    public static String productBloom(){
        return  "product:exists:bloom";
    }
    public static String stockWarmupLock(Long productId) {
        return "stock:warmup:lock:" + productId;
    }
}
