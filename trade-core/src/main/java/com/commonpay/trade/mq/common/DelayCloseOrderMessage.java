package com.commonpay.trade.mq.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 延迟关单：到期后尝试关闭未支付订单。是否真关以当前 payDeadline 为准。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DelayCloseOrderMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TOPIC = "trade-order-delay-close";
    public static final String CONSUMER_GROUP = "trade-order-delay-close-consumer";

    /** RocketMQ 默认 delayLevel：16 = 30m（与下单默认支付窗对齐） */
    public static final int DELAY_LEVEL_PAY_WINDOW = 16;

    private Long orderId;
}
