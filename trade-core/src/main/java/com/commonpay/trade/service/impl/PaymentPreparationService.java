package com.commonpay.trade.service.impl;

import com.commonpay.trade.entity.Order;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.enums.OrderStatus;
import com.commonpay.trade.enums.PaymentStatus;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.service.OrderService;
import com.commonpay.trade.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/** Short database transactions around the remote channel call. */
@Service
@RequiredArgsConstructor
public class PaymentPreparationService {

    /**
     * 首次创建支付单时，至少给用户留这么长的支付窗口。
     * <p>这个值同时也是凭证的有效期 —— 凭证和订单共用同一个截止时间，两边不会各说各话。
     */
    private static final Duration MIN_PAY_WINDOW = Duration.ofMinutes(15);

    private final OrderService orderService;
    private final PaymentService paymentService;

    @Transactional(rollbackFor = Exception.class)
    public Payment prepare(Long orderId, String channel) {
        Order order = orderService.getOrderForUpdate(orderId);
        if (order.getStatus() == null || order.getStatus() != OrderStatus.WAIT_PAY.getCode()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单不是待支付状态");
        }
        if (order.getPayDeadline() == null || !order.getPayDeadline().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单已过期");
        }

        Payment existing = paymentService.getByOrderId(orderId);
        if (existing != null) {
            if (!channel.equals(existing.getChannel())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该订单已有" + existing.getChannel() + "支付单，不能切换渠道");
            }
            if (existing.getStatus() == null || existing.getStatus() == PaymentStatus.SUCCESS.getCode()
                    || existing.getStatus() == PaymentStatus.CLOSED.getCode()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付单已完成或已关闭");
            }
            // 凭证已过期就不能再拉起支付了。这单马上会被关单任务收掉
            if (existing.getExpireTime() == null || !existing.getExpireTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付凭证已过期，请重新下单");
            }
            return existing;
        }

        // 首次创建支付单 —— 只有这一刻才推后截止时间。
        // 每次点支付都推后的话，用户可以一直点、一直不付，库存被无限占用。
        // max 保证只往后推、不往前拉：用户 t=0 下单点支付，不该反而被砍掉时间。
        // 这里在 getOrderForUpdate 的行锁里，改 deadline 是安全的。
        LocalDateTime deadline = order.getPayDeadline();
        // DATETIME 与渠道截止时间都精确到秒，避免 Mock/重试读回数据库后出现纳秒差异。
        LocalDateTime earliestAcceptable = LocalDateTime.now().plus(MIN_PAY_WINDOW).withNano(0);
        if (earliestAcceptable.isAfter(deadline)) {
            orderService.extendPayDeadline(orderId, earliestAcceptable);
            deadline = earliestAcceptable;
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setChannel(channel);
        payment.setAmount(order.getAmount());
        payment.setStatus(PaymentStatus.INIT.getCode());
        // 凭证和订单共用一个截止时间，原样传给渠道 time_expire。
        // 截止时间不能替代关单确认：渠道状态未知时本地仍保留库存，等待重试。
        payment.setExpireTime(deadline);
        if (!paymentService.save(payment)) {
            throw new IllegalStateException("创建支付单失败");
        }
        return payment;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markWaiting(Long paymentId) {
        Payment snapshot = paymentService.getById(paymentId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        Order order = orderService.getOrderForUpdate(snapshot.getOrderId());
        Payment payment = paymentService.getForUpdate(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.SUCCESS.getCode()
                && order.getStatus() != null && order.getStatus() == OrderStatus.PAID.getCode()) {
            return;
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.WAIT_PAY.getCode()
                || order.getPayDeadline() == null || !order.getPayDeadline().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单已取消或已过期，不能继续支付");
        }
        if (payment.getStatus() != null && payment.getStatus() == PaymentStatus.WAITING.getCode()) {
            return;
        }
        if (payment.getStatus() == null || payment.getStatus() != PaymentStatus.INIT.getCode()
                || !paymentService.updateStatus(paymentId, PaymentStatus.INIT.getCode(), PaymentStatus.WAITING.getCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付单状态异常");
        }
    }
}
