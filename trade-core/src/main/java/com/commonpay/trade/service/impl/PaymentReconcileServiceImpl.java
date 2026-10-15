package com.commonpay.trade.service.impl;

import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.pay.api.dto.PayQueryRequest;
import com.commonpay.pay.api.dto.PayQueryResult;
import com.commonpay.pay.api.enums.PayQueryStatus;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.service.PaymentChannelFactory;
import com.commonpay.trade.service.PaymentReconcileService;
import com.commonpay.trade.service.PaymentService;
import com.commonpay.trade.service.PaymentSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconcileServiceImpl implements PaymentReconcileService {

    private final PaymentService paymentService;
    private final PaymentChannelFactory channelFactory;
    private final PaymentSettlementService settlementService;

    @Override
    public boolean settleIfPaid(Long paymentId) {
        return queryAndSettle(paymentId) == PayQueryStatus.PAID;
    }

    @Override
    public PayQueryStatus queryAndSettle(Long paymentId) {
        Payment snapshot = paymentService.getById(paymentId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        PaymentChannel channel = channelFactory.resolve(snapshot.getChannel());
        // 查渠道必须在事务外 —— 远程调用不该占着订单行锁
        PayQueryResult result = channel.queryPay(
                PayQueryRequest.of(paymentId, snapshot.getOrderId(), snapshot.getChannel()));
        if (result == null || result.getStatus() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道查单没有有效结果");
        }
        if (result.getStatus() != PayQueryStatus.PAID) {
            log.info("渠道查单结果为未支付 paymentId={} status={}", paymentId, result.getStatus());
            return result.getStatus();
        }
        if (result.getChannelTradeNo() == null || result.getChannelTradeNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道查单已支付但缺少流水号");
        }
        if (result.getAmount() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道查单已支付但缺少金额，无法入账");
        }
        // 查单无渠道 notifyId：用稳定合成键，避免把 notify_id 写成 NULL，并满足 uk_payment_notify 语义
        String notifyId = "QUERY:" + result.getChannelTradeNo();
        // 结算走另一个 bean：同类自调用不走代理，@Transactional 会失效
        boolean settled = settlementService.settle(snapshot.getChannel(), paymentId,
                result.getChannelTradeNo(), notifyId, result.getAmount());
        log.info("主动查单已处理支付结果 paymentId={} channelTradeNo={}", paymentId, result.getChannelTradeNo());
        if (!settled) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道已支付，但本地尚未完成处理");
        return PayQueryStatus.PAID;
    }
}
