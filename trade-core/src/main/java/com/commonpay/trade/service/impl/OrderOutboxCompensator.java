package com.commonpay.trade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.commonpay.trade.entity.Order;
import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.redis.RedisUtil;
import com.commonpay.trade.service.OrderService;
import com.commonpay.trade.service.OutboxCompensator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 下单消息 FAILED 后归还 Redis 预扣。用 attemptToken 做回补去重，避免重复消息加两次库存。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxCompensator implements OutboxCompensator {

    private final RedisUtil redisUtil;
    private final OrderService orderService;

    @Override
    public String topic() {
        return CreateOrderMessage.TOPIC;
    }

    @Override
    public void compensate(Outbox record) {
        CreateOrderMessage message = JSON.parseObject(record.getPayload(), CreateOrderMessage.class);
        if (message == null || message.getProductId() == null || message.getQuantity() == null) {
            throw new IllegalStateException("Outbox payload 无法解析为 CreateOrderMessage: id=" + record.getId());
        }
        Order exist = orderService.lambdaQuery().eq(Order::getRequestId, message.getRequestId()).one();
        if (exist != null) {
            log.info("订单已存在，跳过预扣归还 requestId={} orderId={}", message.getRequestId(), exist.getOrderId());
            return;
        }
        if (!StringUtils.hasText(message.getAttemptToken())) {
            throw new IllegalStateException("缺失 attemptToken，无法幂等归还 requestId=" + message.getRequestId());
        }
        redisUtil.returnReservationOnce(message.getProductId(), message.getQuantity(), message.getAttemptToken());
        log.info("已归还 Redis 预扣 requestId={} productId={} quantity={}",
                message.getRequestId(), message.getProductId(), message.getQuantity());
    }
}
