package com.commonpay.trade.job;

import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.mapper.PaymentMapper;
import com.commonpay.trade.service.PaymentReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconcileScheduler {
    private final PaymentMapper mapper;
    private final PaymentReconcileService reconcile;
    @Value("${trade.payment-reconcile-closed-days:7}")
    private int closedDays;

    @Scheduled(fixedDelayString = "${trade.payment-reconcile-interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        for (Payment payment : mapper.listReconcileDue(now, now.minusSeconds(30),
                now.minusDays(Math.max(1, closedDays)), 100)) {
            LocalDateTime lease = LocalDateTime.now().plusMinutes(2).withNano(0);
            if (mapper.claimReconcile(payment.getPaymentId(), LocalDateTime.now(), lease) != 1) continue;
            String error = null;
            try {
                reconcile.settleIfPaid(payment.getPaymentId());
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                error = error.substring(0, Math.min(error.length(), 1000));
                log.warn("主动查单失败，轮转到后续批次 paymentId={}", payment.getPaymentId(), e);
            }
            mapper.finishReconcile(payment.getPaymentId(), lease,
                    LocalDateTime.now().plusSeconds(error == null ? 60 : 120), error);
        }
    }
}
