package com.commonpay.trade.job;

import com.commonpay.trade.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 定时关单兜底：延迟 MQ 触达刚到期；本任务按 1 分钟时间片扫漏网 + next_close_at 重试。
 * <p>面向小商户：默认 1 分钟触发一次，与片宽一致，无需按大促量级压测。
 */
@Component
public class OrderCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseScheduler.class);

    private final OrderService orderService;

    public OrderCloseScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${trade.order-close-interval-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void closeExpired() {
        int n = orderService.closeExpiredOrders(100);
        if (n > 0) {
            log.info("closed expired orders count={}", n);
        }
    }
}
