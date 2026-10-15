package com.commonpay.pay.api.dto;

import lombok.Data;

/** 主动查渠道支付结果的入参。 */
@Data
public class PayQueryRequest {

    private Long paymentId;
    private Long orderId;
    private String channel;

    public static PayQueryRequest of(Long paymentId, Long orderId, String channel) {
        PayQueryRequest request = new PayQueryRequest();
        request.setPaymentId(paymentId);
        request.setOrderId(orderId);
        request.setChannel(channel);
        return request;
    }
}
