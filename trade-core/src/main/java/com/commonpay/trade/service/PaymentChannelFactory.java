package com.commonpay.trade.service;

import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registers each enabled payment strategy exactly once at startup. */
@Component
public class PaymentChannelFactory {

    private final Map<String, PaymentChannel> channels;

    public PaymentChannelFactory(List<PaymentChannel> strategies) {
        Map<String, PaymentChannel> registered = new HashMap<>();
        for (PaymentChannel strategy : strategies) {
            String code = strategy.channelCode();
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("支付渠道编码不能为空");
            }
            PaymentChannel previous = registered.putIfAbsent(code.trim().toUpperCase(Locale.ROOT), strategy);
            if (previous != null) {
                throw new IllegalStateException("重复的支付渠道编码: " + code);
            }
        }
        this.channels = Map.copyOf(registered);
    }

    public PaymentChannel resolve(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            throw new BusinessException(ErrorCode.CHANNEL_UNAVAILABLE, "请选择支付方式");
        }
        PaymentChannel channel = channels.get(channelCode.trim().toUpperCase(Locale.ROOT));
        if (channel == null) {
            throw new BusinessException(ErrorCode.CHANNEL_UNAVAILABLE, "尚未接入支付渠道: " + channelCode);
        }
        return channel;
    }
}
