package com.commonpay.trade.common;

/**
 * 对外稳定的错误码。业务状态（已受理、处理中）不属于错误码。
 * {@link #isRetryable()} 供 MQ 等基础设施判断：false 则放弃重试（如商品不存在、库存不足）。
 */
public enum ErrorCode {

    BUSINESS_ERROR(1000, "业务操作未完成", false),
    INVALID_ARGUMENT(1001, "参数校验失败", false),

    PRODUCT_NOT_EXISTS(2001, "商品不存在", false),
    PRODUCT_OFF_SHELF(2002, "商品已下架", false),
    STOCK_NOT_FOUND(2003, "商品库存未配置", false),
    STOCK_INSUFFICIENT(2004, "商品库存不足", false),
    STOCK_NOT_READY(2005, "库存缓存未就绪，请稍后再试", false),

    ORDER_NOT_FOUND(2101, "订单不存在", false),
    IDEMPOTENCY_CONFLICT(2102, "同一受理号不能用于不同的下单请求", false),
    ORDER_REJECTED(2103, "原下单请求已失败，请重新下单", false),

    PAYMENT_NOT_FOUND(2201, "支付单不存在", false),
    PAYMENT_INVALID_STATE(2202, "支付单状态不允许此操作", false),
    CHANNEL_UNAVAILABLE(2203, "支付渠道不可用或未接入", false),

    SYSTEM_ERROR(5000, "系统异常，请稍后再试", true),
    REDIS_UNAVAILABLE(5001, "库存服务暂不可用，请稍后再试", true),
    ORDER_RESULT_UNKNOWN(5002, "下单结果尚未确认，请使用原受理号查询进度", true),
    ORDER_ACCEPT_FAILED(5003, "下单未受理成功，请稍后重新下单", false),
    REFUND_MISMATCH(5004, "退款结果需要核对", false),
    NOT_IMPLEMENTED(5005, "当前功能尚未接入", false);

    private final int code;
    private final String message;
    /** MQ/定时任务遇到此码时：false = 放弃重试并走失败收口；true = 可再试。 */
    private final boolean retryable;

    ErrorCode(int code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
