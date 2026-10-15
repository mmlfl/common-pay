package com.commonpay.trade.service.impl;

import com.commonpay.trade.entity.Order;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.enums.OrderStatus;
import com.commonpay.trade.enums.PaymentStatus;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.mapper.OrderMapper;
import com.commonpay.trade.service.PaymentService;
import com.commonpay.trade.service.PaymentSettlementService;
import com.commonpay.trade.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSettlementServiceImpl implements PaymentSettlementService {

    /** 直接用 OrderMapper 而不是 OrderService：否则 OrderService → 这里 → OrderService 会成环 */
    private final OrderMapper orderMapper;
    private final PaymentService paymentService;
    private final RefundService refundService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settle(String channelCode, Long paymentId, String channelTradeNo,
                          String notifyId, BigDecimal amount) {
        if (channelCode == null || channelTradeNo == null || channelTradeNo.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付结果缺少渠道或流水号");
        }
        Payment snapshot = paymentService.getById(paymentId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        // 加锁顺序和关单任务保持一致：先 order，再 payment
        Order order = orderMapper.selectForUpdate(snapshot.getOrderId());
        Payment payment = paymentService.getForUpdate(paymentId);
        if (order == null || payment == null || !payment.getOrderId().equals(order.getOrderId())
                || !channelCode.equalsIgnoreCase(payment.getChannel())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付结果与支付单不匹配");
        }
        if (amount == null || payment.getAmount() == null
                || amount.compareTo(payment.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付金额与支付单不一致");
        }
        if (order.getAmount() == null || order.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付金额与订单不一致");
        }
        if (payment.getChannelTradeNo() != null && !channelTradeNo.equals(payment.getChannelTradeNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付流水与已记录的交易不一致");
        }
        if (order.getStatus() == null || (order.getStatus() != OrderStatus.WAIT_PAY.getCode()
                && order.getStatus() != OrderStatus.PAID.getCode()
                && order.getStatus() != OrderStatus.CANCELLED.getCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单状态异常，需人工核对");
        }
        if (payment.getStatus() == null) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付单状态异常");
        if (payment.getStatus() != PaymentStatus.SUCCESS.getCode()) {
            // 包括 CLOSED：本地关闭不能否认渠道已扣款的事实。
            if (!paymentService.markSuccess(paymentId, channelTradeNo, notifyId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "保存渠道支付事实失败");
            }
            payment.setStatus(PaymentStatus.SUCCESS.getCode());
            payment.setChannelTradeNo(channelTradeNo);
        }
        if (order.getStatus() == OrderStatus.CANCELLED.getCode()) {
            // 已取消的订单库存已归还，不能恢复成已支付或再次操作库存。
            // 记录支付事实和退款义务在同一事务，提交后再由任务调退款渠道。
            refundService.ensureCancelledOrderRefund(payment);
            log.warn("已取消订单收到支付成功，已记录待退款 orderId={} paymentId={}",
                    order.getOrderId(), paymentId);
            return true;
        }
        if (order.getStatus() == OrderStatus.WAIT_PAY.getCode()
                && orderMapper.updateStatus(order.getOrderId(),
                OrderStatus.WAIT_PAY.getCode(), OrderStatus.PAID.getCode()) != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "更新订单支付状态失败");
        }
        return true;
    }
}
