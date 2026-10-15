package com.commonpay.trade.job;

import com.alibaba.fastjson2.JSON;
import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.enums.OutboxStatus;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 旧版：扫 PENDING 定时发送。半事务上线后默认关闭。
 * <p>应急回退：{@code trade.outbox.send-job-enabled=true}
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "trade.outbox", name = "send-job-enabled", havingValue = "true")
public class OutboxSendJob {

    private static final int BATCH_SIZE = 100;

    private final OutboxService outboxService;
    private final RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelayString = "${trade.outbox-send-interval-seconds:2}", timeUnit = TimeUnit.SECONDS)
    public void send() {
        List<Outbox> pending = outboxService.lambdaQuery()
                .eq(Outbox::getStatus, OutboxStatus.PENDING.getCode())
                .orderByAsc(Outbox::getId)
                .last("LIMIT " + BATCH_SIZE)
                .list();
        if (pending.isEmpty()) {
            return;
        }

        for (Outbox record : pending) {
            try {
                CreateOrderMessage message = JSON.parseObject(record.getPayload(), CreateOrderMessage.class);
                rocketMQTemplate.syncSend(record.getTopic(), message);
                outboxService.lambdaUpdate()
                        .eq(Outbox::getId, record.getId())
                        .eq(Outbox::getStatus, OutboxStatus.PENDING.getCode())
                        .set(Outbox::getStatus, OutboxStatus.SENT.getCode())
                        .update();
            } catch (Exception e) {
                log.warn("outbox 发送失败，等待下轮重试 id={} requestId={}",
                        record.getId(), record.getRequestId(), e);
                countRetry(record.getId());
            }
        }
    }

    private void countRetry(Long id) {
        try {
            outboxService.lambdaUpdate()
                    .eq(Outbox::getId, id)
                    .eq(Outbox::getStatus, OutboxStatus.PENDING.getCode())
                    .setSql("retry_count = retry_count + 1")
                    .update();
        } catch (Exception e) {
            log.warn("retry_count 自增失败 id={}", id, e);
        }
    }
}
