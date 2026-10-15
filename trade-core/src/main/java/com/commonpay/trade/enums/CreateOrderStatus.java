package com.commonpay.trade.enums;

/**
 * 下单正常流程状态；失败通过带错误码的异常表达。
 * <p>
 * 异步下单：Redis 扣库存成功后返回 {@link #ACCEPTED}，正式订单由 MQ 消费者落库。
 */
public enum CreateOrderStatus {

    /**
     * 同步下单成功，{@code orderNo} 有效。
     * （走 DB 同步链路时用；异步链路成功用 {@link #ACCEPTED}）
     */
    OK,

    /**
     * 异步受理成功：库存已扣、消息已投递（或待投递），{@code requestId} 有效；
     * 此时尚无最终 {@code orderNo}，需靠 requestId 查进度 / 等回调。
     */
    ACCEPTED,

    /** 相同请求正在执行，尚未确认受理；客户端继续查原 requestId，不能重复预扣。 */
    PROCESSING
}
