package com.commonpay.trade.job;

import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.enums.OutboxStatus;
import com.commonpay.trade.service.OutboxService;
import com.commonpay.trade.service.impl.OutboxCompensatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 扫 FAILED Outbox：先 CAS 认领为 COMPENSATED，再按 topic 做业务收尾（如下单归还 Redis）。
 * 收尾失败则退回 FAILED，下轮重试。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCompensateJob {

    private static final int BATCH_SIZE = 100;

    private final OutboxService outboxService;
    private final OutboxCompensatorFactory compensatorFactory;

    @Scheduled(fixedDelayString = "${trade.outbox-compensate-interval-seconds:10}", timeUnit = TimeUnit.SECONDS)
    public void compensate() {
        List<Outbox> failed = outboxService.lambdaQuery()
                .eq(Outbox::getStatus, OutboxStatus.FAILED.getCode())
                .orderByAsc(Outbox::getId)
                .last("LIMIT " + BATCH_SIZE)
                .list();
        if (failed.isEmpty()) {
            return;
        }
        for (Outbox record : failed) {
            try {
                compensateOne(record);
            } catch (Exception e) {
                log.warn("补偿失败，等待下轮重试 id={} requestId={}", record.getId(), record.getRequestId(), e);
            }
        }
    }

    private void compensateOne(Outbox record) {
        if (!casStatus(record.getId(), OutboxStatus.FAILED, OutboxStatus.COMPENSATED)) {
            return;
        }
        try {
            compensatorFactory.get(record.getTopic()).compensate(record);
        } catch (Exception e) {
            casStatus(record.getId(), OutboxStatus.COMPENSATED, OutboxStatus.FAILED);
            throw e;
        }
    }

    private boolean casStatus(Long id, OutboxStatus from, OutboxStatus to) {
        return outboxService.lambdaUpdate()
                .eq(Outbox::getId, id)
                .eq(Outbox::getStatus, from.getCode())
                .set(Outbox::getStatus, to.getCode())
                .update();
    }
}
