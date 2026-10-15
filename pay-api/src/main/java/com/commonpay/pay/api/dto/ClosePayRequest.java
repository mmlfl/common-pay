package com.commonpay.pay.api.dto;

import lombok.Data;

/** 关闭渠道支付凭证的入参。 */
@Data
public class ClosePayRequest {

    private Long paymentId;
    private Long orderId;
    private String channel;

    public static ClosePayRequest of(Long paymentId, Long orderId, String channel) {
        ClosePayRequest request = new ClosePayRequest();
        request.setPaymentId(paymentId);
        request.setOrderId(orderId);
        request.setChannel(channel);
        return request;
    }
}
