package com.commonpay.pay.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 渠道回调解析后的统一结果。
 */
@Data
public class PayNotifyResult {

    private Long paymentId;
    private String channel;
    private String notifyId;
    private String channelTradeNo;
    private BigDecimal amount;
    private boolean success;
}
