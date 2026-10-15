package com.commonpay.trade.job;

import com.commonpay.trade.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RefundScheduler {
    private final RefundService refundService;
    @Scheduled(fixedDelayString = "${trade.refund-interval-seconds:15}", timeUnit = TimeUnit.SECONDS)
    public void process() { refundService.processDue(100); }
}
