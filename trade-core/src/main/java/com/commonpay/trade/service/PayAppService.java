package com.commonpay.trade.service;

import com.commonpay.pay.api.dto.CreatePayResponse;
import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.trade.entity.Payment;

/**
 * 支付应用编排（跨订单/支付单/渠道），不是单表 CRUD。
 * 单表请用 {@link PaymentService} / {@link OrderService}。
 */
public interface PayAppService {

    CreatePayResponse pay(Long orderId, String channel);

    default CreatePayResponse pay(Long orderId) {
        return pay(orderId, "MOCK");
    }

    void handlePaySuccess(PayNotifyCommand command);

    Payment getByOrderId(Long orderId);
}
