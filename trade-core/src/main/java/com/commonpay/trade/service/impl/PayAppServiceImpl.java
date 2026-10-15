package com.commonpay.trade.service.impl;

import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.pay.api.dto.CreatePayRequest;
import com.commonpay.pay.api.dto.CreatePayResponse;
import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.pay.api.dto.PayNotifyResult;
import com.commonpay.pay.api.dto.ClosePayRequest;
import com.commonpay.pay.api.enums.ClosePayStatus;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.service.PayAppService;
import com.commonpay.trade.service.PaymentChannelFactory;
import com.commonpay.trade.service.PaymentService;
import com.commonpay.trade.service.PaymentSettlementService;
import com.commonpay.trade.service.PaymentReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * 支付编排：先提交本地支付单，再调渠道，最后更新等待支付状态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayAppServiceImpl implements PayAppService {

    private final PaymentService paymentService;
    private final PaymentPreparationService paymentPreparationService;
    private final PaymentChannelFactory channelFactory;
    private final PaymentSettlementService settlementService;
    private final PaymentReconcileService reconcileService;

    @Override
    public CreatePayResponse pay(Long orderId, String channelCode) {
        PaymentChannel channel = channelFactory.resolve(channelCode);
        Payment payment = paymentPreparationService.prepare(orderId, channel.channelCode());

        // The local INIT row is committed before talking to a remote provider.
        // A failed or timed-out createPay can be retried with the same paymentId.
        CreatePayRequest request = new CreatePayRequest();
        request.setPaymentId(payment.getPaymentId());
        request.setOrderId(orderId);
        request.setAmount(payment.getAmount());
        request.setSubject("订单 " + orderId);
        // 过期时间必须传给渠道，让两边指向同一时刻
        request.setExpireTime(payment.getExpireTime());
        CreatePayResponse response = channel.createPay(request);
        if (response == null || response.getPayPayload() == null || response.getPayPayload().isBlank()) {
            throw new IllegalStateException("支付渠道未返回支付参数");
        }
        try {
            paymentPreparationService.markWaiting(payment.getPaymentId());
        } catch (RuntimeException e) {
            // 预创建请求可能跨过截止时间才返回。此时不向前端交付二维码，并再关一次渠道。
            // 失败由后台关单/查单继续恢复；付款已完成则走同一套入账/退款分支。
            if (!payment.getExpireTime().isAfter(LocalDateTime.now())) {
                try {
                    var closed = channel.closePay(ClosePayRequest.of(payment.getPaymentId(), orderId, channel.channelCode()));
                    if (closed != null && closed.getStatus() == ClosePayStatus.ALREADY_PAID
                            && reconcileService.settleIfPaid(payment.getPaymentId())) {
                        // 补偿入账已成功：勿再抛 markWaiting 原始异常，避免调用方当成发起失败去重试预创建
                        throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE, "订单已支付完成");
                    }
                } catch (BusinessException paid) {
                    throw paid;
                } catch (RuntimeException closeError) {
                    log.warn("支付预创建迟到，补偿关单未确认 paymentId={}", payment.getPaymentId(), closeError);
                }
            }
            throw e;
        }
        response.setPaymentId(payment.getPaymentId());
        response.setChannel(channel.channelCode());
        return response;
    }

    @Override
    public void handlePaySuccess(PayNotifyCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付通知不能为空");
        }
        PaymentChannel channel = channelFactory.resolve(command.getChannel());
        PayNotifyResult result = channel.parseNotify(command);
        if (result == null || result.getPaymentId() == null
                || !channel.channelCode().equalsIgnoreCase(result.getChannel())
                || (command.getPaymentId() != null && !command.getPaymentId().equals(result.getPaymentId()))
                || result.getNotifyId() == null || result.getNotifyId().isBlank()
                || result.getChannelTradeNo() == null || result.getChannelTradeNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付通知内容不完整或渠道不匹配");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付未成功");
        }
        // 入账逻辑和主动查单路径共用，见 PaymentSettlementService
        settlementService.settle(result.getChannel(), result.getPaymentId(),
                result.getChannelTradeNo(), result.getNotifyId(), result.getAmount());
    }

    @Override
    public Payment getByOrderId(Long orderId) {
        return paymentService.getByOrderId(orderId);
    }
}
