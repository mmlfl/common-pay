package com.commonpay.trade.mq;

import com.commonpay.trade.mq.common.DelayCloseOrderMessage;
import com.commonpay.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 延迟关单消费：与 {@link com.commonpay.trade.job.OrderCloseScheduler} 共用 tryCloseIfExpired。
 * 若首付延长了 payDeadline，消息到达时可能未过期 —— 直接跳过，交给定时任务稍后扫到。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = DelayCloseOrderMessage.TOPIC, consumerGroup = DelayCloseOrderMessage.CONSUMER_GROUP)
public class DelayCloseOrderConsumer implements RocketMQListener<DelayCloseOrderMessage> {

    private final OrderService orderService;

    @Override
    public void onMessage(DelayCloseOrderMessage message) {
        if (message == null || message.getOrderId() == null) {
            return;
        }
        boolean closed = orderService.tryCloseIfExpired(message.getOrderId());
        log.info("延迟关单处理完成 orderId={} closed={}", message.getOrderId(), closed);
    }
}
