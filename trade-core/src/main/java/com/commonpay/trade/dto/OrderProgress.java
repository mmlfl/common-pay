package com.commonpay.trade.dto;

/**
 * 异步下单进度。前端拿 requestId 轮询它，直到 status 不是 PENDING。
 * <p>orderId 用 String：雪花 ID 超出 JS 安全整数范围。
 */
public record OrderProgress(String status, String orderId) {

    /** 还在处理中（也可能永远不会好，前端要自己设超时） */
    public static final String PENDING = "PENDING";

    /** 订单已创建 */
    public static final String CREATED = "CREATED";

    /** 已确定下单失败；库存补偿可能仍在恢复任务中执行。 */
    public static final String FAILED = "FAILED";

    public static OrderProgress pending() {
        return new OrderProgress(PENDING, null);
    }

    public static OrderProgress created(Long orderId) {
        return new OrderProgress(CREATED, String.valueOf(orderId));
    }

    public static OrderProgress failed() {
        return new OrderProgress(FAILED, null);
    }
}
