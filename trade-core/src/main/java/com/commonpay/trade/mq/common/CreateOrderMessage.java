package com.commonpay.trade.mq.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TOPIC = "ticket-order-create";
    public static final String CONSUMER_GROUP = "ticket-order-consumer";

    /** 受理号,一次下单请求一个 */
    private String requestId;

    private String userId;
    private Long productId;
    private Integer quantity;

    /**
     * 本次预扣去重令牌（幂等 claim.token）。
     * 写入 Outbox payload，补偿归还时不依赖可能已过期的幂等缓存。
     */
    private String attemptToken;

}
