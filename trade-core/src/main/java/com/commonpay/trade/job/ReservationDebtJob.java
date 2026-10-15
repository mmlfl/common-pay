package com.commonpay.trade.job;

import com.commonpay.trade.service.impl.ReservationDebtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 扫「预扣成功但未进 Outbox」的崩溃遗留义务，幂等归还 Redis。
 * <p>仅兜底极端崩溃，默认 30 分钟跑一轮；关闭：{@code trade.reservation-debt.enabled=false}
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "trade.reservation-debt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReservationDebtJob {

    private static final int BATCH_SIZE = 100;

    private final ReservationDebtService reservationDebtService;

    /**
     * 义务存活超过该秒数才对账。默认 15 分钟，需覆盖「半消息发送卡住」最坏时长，
     * 避免无 Outbox 时过早回补、随后受理成功导致超卖。
     */
    @Value("${trade.reservation-debt.age-seconds:900}")
    private long ageSeconds;

    @Scheduled(fixedDelayString = "${trade.reservation-debt.interval-seconds:1800}", timeUnit = TimeUnit.SECONDS)
    public void reconcile() {
        int n = reservationDebtService.reconcileDue(Duration.ofSeconds(Math.max(1, ageSeconds)), BATCH_SIZE);
        if (n > 0) {
            log.info("预扣义务对账完成 count={}", n);
        }
    }
}
