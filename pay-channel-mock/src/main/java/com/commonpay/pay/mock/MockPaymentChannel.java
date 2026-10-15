package com.commonpay.pay.mock;

import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.pay.api.dto.*;
import com.commonpay.pay.api.enums.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 演示渠道：付款和关单锁定同一笔渠道单，通知只读取已发生的支付事实。 */
@Component
public class MockPaymentChannel implements PaymentChannel {
    public static final String CHANNEL_CODE = "MOCK";
    private enum State { WAITING, PAID, CLOSED }
    private static final class MockPayment {
        final BigDecimal amount;
        final LocalDateTime expires;
        State state = State.WAITING;
        String refundNo;
        boolean failNextClose, refundPending, refundTimeoutAfterAccept;
        MockPayment(BigDecimal amount, LocalDateTime expires) { this.amount=amount; this.expires=expires; }
    }
    private static final class MockRefund {
        final RefundRequest request;
        RefundQueryStatus status;
        LocalDateTime refundedAt;
        MockRefund(RefundRequest request, boolean pending) {
            this.request=request;
            status=pending ? RefundQueryStatus.PROCESSING : RefundQueryStatus.SUCCESS;
            refundedAt=pending ? null : LocalDateTime.now();
        }
    }
    private final Map<Long, MockPayment> payments = new ConcurrentHashMap<>();
    private final Map<String, MockRefund> refunds = new ConcurrentHashMap<>();

    @Override public String channelCode() { return CHANNEL_CODE; }

    @Override
    public CreatePayResponse createPay(CreatePayRequest request) {
        if (request == null || request.getPaymentId() == null || request.getAmount() == null
                || request.getAmount().signum() <= 0 || request.getExpireTime() == null) {
            throw new IllegalArgumentException("Mock 支付参数不完整");
        }
        MockPayment payment = payments.computeIfAbsent(request.getPaymentId(),
                id -> new MockPayment(request.getAmount(), request.getExpireTime()));
        synchronized (payment) {
            if (payment.amount.compareTo(request.getAmount()) != 0
                    || !payment.expires.equals(request.getExpireTime())) {
                throw new IllegalArgumentException("同一支付单不能更改金额或截止时间");
            }
            if (payment.state == State.CLOSED || (payment.state != State.PAID
                    && !payment.expires.isAfter(LocalDateTime.now()))) {
                throw new IllegalStateException("支付凭证已关闭或过期");
            }
            return CreatePayResponse.builder().paymentId(request.getPaymentId()).channel(CHANNEL_CODE)
                    .payPayload("/api/mock-pay/" + request.getPaymentId() + "/success").build();
        }
    }

    @Override
    public ClosePayResult closePay(ClosePayRequest request) {
        if (request == null || request.getPaymentId() == null) throw new IllegalArgumentException("缺少支付单号");
        MockPayment payment = payments.get(request.getPaymentId());
        if (payment == null) return ClosePayResult.of(ClosePayStatus.NOT_FOUND);
        synchronized (payment) {
            if (payment.failNextClose) {
                payment.failNextClose=false;
                throw new IllegalStateException("Mock 模拟渠道关单超时");
            }
            if (payment.state == State.PAID) {
                ClosePayResult result=ClosePayResult.of(ClosePayStatus.ALREADY_PAID);
                result.setChannelTradeNo(channelTradeNo(request.getPaymentId()));
                return result;
            }
            payment.state=State.CLOSED;
            return ClosePayResult.of(ClosePayStatus.CLOSED);
        }
    }

    /** 模拟扣款；支付和关单共用同一把锁，正常付款不会突破 CLOSED/过期状态。 */
    public void markPaidWithoutNotify(Long paymentId) {
        MockPayment payment=required(paymentId);
        synchronized (payment) {
            if (payment.state == State.PAID) return;
            if (payment.state == State.CLOSED || !payment.expires.isAfter(LocalDateTime.now())) {
                throw new IllegalStateException("支付凭证已关闭或过期，无法支付");
            }
            payment.state=State.PAID;
        }
    }

    public PayNotifyCommand successNotification(Long paymentId, BigDecimal amount, String notifyId) {
        MockPayment payment=required(paymentId);
        synchronized (payment) {
            if (payment.state != State.PAID || amount == null || payment.amount.compareTo(amount) != 0) {
                throw new IllegalArgumentException("渠道尚未付款或通知金额不匹配");
            }
            PayNotifyCommand command=new PayNotifyCommand();
            command.setChannel(CHANNEL_CODE);
            command.setPaymentId(paymentId);
            command.setNotifyId(notifyId == null || notifyId.isBlank() ? "MOCK-" + UUID.randomUUID() : notifyId);
            command.setChannelTradeNo(channelTradeNo(paymentId));
            command.setAmount(payment.amount);
            command.setSuccess(true);
            return command;
        }
    }

    @Override
    public PayNotifyResult parseNotify(PayNotifyCommand command) {
        if (command == null || !CHANNEL_CODE.equalsIgnoreCase(command.getChannel())
                || command.getNotifyId() == null || command.getNotifyId().isBlank()
                || !channelTradeNo(command.getPaymentId()).equals(command.getChannelTradeNo())
                || !command.isSuccess()) throw new IllegalArgumentException("无效的 Mock 支付通知");
        MockPayment payment=required(command.getPaymentId());
        synchronized (payment) {
            // 已经付款的通知可以延迟到凭证过期后送达；解析通知不制造扣款。
            if (payment.state != State.PAID || command.getAmount() == null
                    || payment.amount.compareTo(command.getAmount()) != 0) {
                throw new IllegalArgumentException("Mock 通知与渠道支付事实不一致");
            }
            PayNotifyResult result=new PayNotifyResult();
            result.setPaymentId(command.getPaymentId());
            result.setChannel(CHANNEL_CODE);
            result.setNotifyId(command.getNotifyId());
            result.setChannelTradeNo(command.getChannelTradeNo());
            result.setAmount(payment.amount);
            result.setSuccess(true);
            return result;
        }
    }

    @Override
    public PayQueryResult queryPay(PayQueryRequest request) {
        if (request == null || request.getPaymentId() == null) throw new IllegalArgumentException("缺少支付单号");
        MockPayment payment=payments.get(request.getPaymentId());
        if (payment == null) return PayQueryResult.of(PayQueryStatus.NOT_FOUND);
        synchronized (payment) {
            if (payment.state == State.CLOSED) return PayQueryResult.of(PayQueryStatus.CLOSED);
            if (payment.state != State.PAID) return PayQueryResult.of(PayQueryStatus.NOT_PAID);
            PayQueryResult result=PayQueryResult.of(PayQueryStatus.PAID);
            result.setChannelTradeNo(channelTradeNo(request.getPaymentId()));
            result.setAmount(payment.amount);
            return result;
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        MockPayment payment=required(request.getPaymentId());
        synchronized (payment) {
            validateRefund(request, payment);
            if (payment.refundNo != null && !payment.refundNo.equals(request.getRefundNo())) {
                throw new IllegalArgumentException("该交易已有全额退款，必须复用原退款号");
            }
            MockRefund refund=refunds.computeIfAbsent(request.getRefundNo(),
                    id -> new MockRefund(request, payment.refundPending));
            if (!refund.request.getPaymentId().equals(request.getPaymentId())
                    || refund.request.getRefundAmount().compareTo(request.getRefundAmount()) != 0) {
                throw new IllegalArgumentException("退款号重复但参数不同");
            }
            payment.refundNo=request.getRefundNo();
            if (payment.refundTimeoutAfterAccept) {
                payment.refundTimeoutAfterAccept=false;
                throw new IllegalStateException("Mock 已受理退款但模拟响应丢失，请查原退款号");
            }
            synchronized (refund) { return refundResult(refund); }
        }
    }

    @Override
    public RefundResult queryRefund(RefundRequest request) {
        MockRefund refund=refunds.get(request.getRefundNo());
        if (refund == null) return RefundResult.notFound();
        synchronized (refund) { return refundResult(refund); }
    }

    /** 只给显式启用的 mock-lab 入口调用，模拟历史系统误关后才发现扣款。 */
    public void injectLatePaidForDemo(Long paymentId) {
        MockPayment payment=required(paymentId);
        synchronized (payment) { payment.state=State.PAID; }
    }
    public void configureScenario(Long id, boolean failClose, boolean pendingRefund, boolean loseRefundResponse) {
        MockPayment payment=required(id);
        synchronized (payment) {
            payment.failNextClose=failClose;
            payment.refundPending=pendingRefund;
            payment.refundTimeoutAfterAccept=loseRefundResponse;
        }
    }
    public void completeRefundForDemo(String refundNo) {
        MockRefund refund=refunds.get(refundNo);
        if (refund == null) throw new IllegalArgumentException("渠道退款单不存在");
        synchronized (refund) {
            refund.status=RefundQueryStatus.SUCCESS;
            refund.refundedAt=LocalDateTime.now();
        }
    }
    private MockPayment required(Long id) {
        MockPayment payment=id == null ? null : payments.get(id);
        if (payment == null) throw new IllegalArgumentException("渠道没有这笔单: " + id);
        return payment;
    }
    private void validateRefund(RefundRequest request, MockPayment payment) {
        if (request.getRefundNo() == null || request.getRefundNo().isBlank() || payment.state != State.PAID
                || !channelTradeNo(request.getPaymentId()).equals(request.getChannelTradeNo())
                || request.getTotalAmount() == null || payment.amount.compareTo(request.getTotalAmount()) != 0
                || request.getRefundAmount() == null || payment.amount.compareTo(request.getRefundAmount()) != 0) {
            throw new IllegalArgumentException("Mock 只支持该笔已支付交易的全额退款");
        }
    }
    private RefundResult refundResult(MockRefund refund) {
        RefundResult result=new RefundResult();
        result.setStatus(refund.status);
        result.setRefundNo(refund.request.getRefundNo());
        result.setPaymentId(refund.request.getPaymentId());
        result.setChannelTradeNo(refund.request.getChannelTradeNo());
        result.setChannelRefundNo("MOCK-REFUND-" + refund.request.getRefundNo());
        result.setAmount(refund.request.getRefundAmount());
        result.setRefundedAt(refund.refundedAt);
        return result;
    }
    private String channelTradeNo(Long id) { return "MOCK-TXN-" + id; }
}
