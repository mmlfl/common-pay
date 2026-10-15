package com.commonpay.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.pay.api.dto.*;
import com.commonpay.pay.api.enums.*;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.entity.Refund;
import com.commonpay.trade.enums.RefundStatus;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.mapper.RefundMapper;
import com.commonpay.trade.service.PaymentChannelFactory;
import com.commonpay.trade.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl extends ServiceImpl<RefundMapper, Refund> implements RefundService {
    private final PaymentChannelFactory channelFactory;
    private final RefundStateService stateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Refund ensureCancelledOrderRefund(Payment payment) {
        // 调用方已经按 order→payment 加锁，重复回调/主动查单在此串行。
        Refund existing = lambdaQuery().eq(Refund::getPaymentId, payment.getPaymentId()).one();
        if (existing != null) {
            if (!existing.getChannelTradeNo().equals(payment.getChannelTradeNo())
                    || existing.getAmount().compareTo(payment.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已有退款单与迟到支付不一致");
            }
            return existing;
        }
        Refund refund = new Refund();
        refund.setPaymentId(payment.getPaymentId());
        refund.setOrderId(payment.getOrderId());
        refund.setChannel(payment.getChannel());
        refund.setChannelTradeNo(payment.getChannelTradeNo());
        refund.setAmount(payment.getAmount());
        refund.setReason("订单已取消，退还迟到支付款");
        refund.setStatus(RefundStatus.PENDING.getCode());
        refund.setRetryCount(0);
        refund.setNextRetryAt(LocalDateTime.now());
        if (!save(refund)) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "创建待退款记录失败");
        return refund;
    }

    @Override
    public Refund getByOrderId(Long orderId) {
        return lambdaQuery().eq(Refund::getOrderId, orderId).one();
    }

    @Override
    public int processDue(int batchSize) {
        int processed = 0;
        for (Refund refund : baseMapper.due(LocalDateTime.now(), batchSize)) {
            String token = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            if (baseMapper.claim(refund.getRefundId(), token, now, now.plusMinutes(3)) != 1) continue;
            try {
                PaymentChannel channel = channelFactory.resolve(refund.getChannel());
                RefundRequest request = request(refund);
                // 上次可能已退款但响应丢了。每次先查，绝不换新退款号。
                RefundResult result = channel.queryRefund(request);
                if (result == null || result.getStatus() == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "退款查询无有效结果");
                }
                if (result.getStatus() == RefundQueryStatus.NOT_FOUND) {
                    PayQueryResult paid = channel.queryPay(PayQueryRequest.of(
                            refund.getPaymentId(), refund.getOrderId(), refund.getChannel()));
                    if (paid == null || paid.getStatus() != PayQueryStatus.PAID
                            || !refund.getChannelTradeNo().equals(paid.getChannelTradeNo())
                            || paid.getAmount() == null || refund.getAmount().compareTo(paid.getAmount()) != 0) {
                        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "退款前未能确认原支付交易");
                    }
                    result = channel.refund(request);
                }
                stateService.apply(refund.getRefundId(), refund.getChannel(), token, result);
                processed++;
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.REFUND_MISMATCH) {
                    baseMapper.manual(refund.getRefundId(), token, e.getMessage());
                    log.error("退款证据冲突，转人工核对 refundId={}", refund.getRefundId(), e);
                    continue;
                }
                scheduleRefundRetry(refund, token, e);
            } catch (Exception e) {
                scheduleRefundRetry(refund, token, e);
            }
        }
        return processed;
    }

    private void scheduleRefundRetry(Refund refund, String token, Exception e) {
        long seconds = Math.min(3600L, 30L * (1L << Math.min(refund.getRetryCount(), 7)));
        String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        baseMapper.retry(refund.getRefundId(), token, LocalDateTime.now().plusSeconds(seconds),
                error.substring(0, Math.min(error.length(), 1000)));
        log.warn("退款结果未知/请求失败，保留原退款号重试 refundId={}", refund.getRefundId(), e);
    }

    private RefundRequest request(Refund refund) {
        RefundRequest request = new RefundRequest();
        request.setRefundNo(refund.getRefundId().toString());
        request.setPaymentId(refund.getPaymentId());
        request.setChannelTradeNo(refund.getChannelTradeNo());
        request.setTotalAmount(refund.getAmount());
        request.setRefundAmount(refund.getAmount());
        request.setReason(refund.getReason());
        return request;
    }
}
