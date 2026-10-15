package com.commonpay.trade.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 关单时间片并行：一片一任务。池不宜过大（片内会调渠道关单）。
 */
@Configuration
public class OrderCloseExecutorConfig {

    public static final String ORDER_CLOSE_EXECUTOR = "orderCloseExecutor";

    @Bean(name = ORDER_CLOSE_EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService orderCloseExecutor(
            @Value("${trade.order-close.pool-size:4}") int poolSize) {
        int size = Math.max(1, poolSize);
        return new ThreadPoolExecutor(
                size,
                size,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                r -> {
                    Thread t = new Thread(r, "order-close-slice");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
