package com.commonpay.trade.mq;

import com.commonpay.trade.dto.CreateOrderResult;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = CreateOrderMessage.TOPIC, consumerGroup = CreateOrderMessage.CONSUMER_GROUP)
public class OrderConsumer implements RocketMQListener<CreateOrderMessage> {

    private final OrderService orderService;

    @Override
    public void onMessage(CreateOrderMessage message) {
        log.info("收到下单消息 requestId={} productId={}", message.getRequestId(), message.getProductId());
        try {
            CreateOrderResult order = orderService.createOrderFromMq(message);
            log.info("订单创建成功 orderId={}", order.getOrderNo());
        } catch (BusinessException e) {
            if (!e.isRetryable()) {
                // 业务已确定失败：标 FAILED + 正常 return（= ACK），不让 RocketMQ 空转重试。
                // Redis 预扣由 OutboxCompensateJob 扫 FAILED 归还，不是 giveUp 里直接加库存。
                orderService.giveUp(message, e.getMessage());
                return;
            }
            // 可重试业务码：故意再抛，让 RocketMQ 重投。当前 createOrderFromMq 抛出的业务码均为不可重试。
            throw e;
        }
        // 其它异常（含 BaseException 系统类）不 catch → 天然触发 MQ 重试
    }
}
