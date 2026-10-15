package com.commonpay.trade.mq;

import com.alibaba.fastjson2.JSON;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.service.impl.OrderAcceptanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrderTransactionHandler implements TransactionMessageHandler {

    public static final String HANDLER_NAME = "CREATE_ORDER";

    private final OrderAcceptanceService acceptanceService;

    @Override
    public String handlerName() {
        return HANDLER_NAME;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message<?> msg, Object arg) {
        CreateOrderMessage body = (CreateOrderMessage) arg;
        try {
            acceptanceService.persistAccepted(body);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (RuntimeException e) {
            log.error("本地受理失败，ROLLBACK requestId={}", body.getRequestId(), e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message<?> msg) {
        String requestId = resolveRequestId(msg);
        log.info("回查 requestId={}", requestId);
        // 解析失败属于不确定，不能 ROLLBACK，否则半消息被丢而 Outbox/预扣无法收敛
        if (requestId == null || requestId.isBlank()) {
            log.error("回查失败：无法解析 requestId，返回 UNKNOWN 待再查");
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        try {
            if (acceptanceService.existsByRequestId(requestId)) {
                return RocketMQLocalTransactionState.COMMIT;
            }
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (RuntimeException e) {
            log.error("回查 Outbox 异常，返回 UNKNOWN 待再查 requestId={}", requestId, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    private static String resolveRequestId(Message<?> msg) {
        Object payload = msg.getPayload();
        CreateOrderMessage body = null;
        if (payload instanceof CreateOrderMessage m) {
            body = m;
        } else if (payload instanceof byte[] bytes) {
            body = JSON.parseObject(bytes, CreateOrderMessage.class);
        } else if (payload instanceof String s) {
            body = JSON.parseObject(s, CreateOrderMessage.class);
        }
        if (body != null && body.getRequestId() != null && !body.getRequestId().isBlank()) {
            return body.getRequestId();
        }
        Object header = msg.getHeaders().get("requestId");
        return header == null ? null : String.valueOf(header);
    }
}
