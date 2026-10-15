package com.commonpay.trade.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 唯一半事务监听器：按消息头 {@link #HANDLER_NAME_HEADER} 分发到各 {@link TransactionMessageHandler}。
 */
@Component
@Slf4j
@RocketMQTransactionListener
public class GlobalTransactionListener implements RocketMQLocalTransactionListener {

    public static final String HANDLER_NAME_HEADER = "HANDLER_NAME";

    private final Map<String, TransactionMessageHandler> handlerMap;

    public GlobalTransactionListener(List<TransactionMessageHandler> handlers) {
        Map<String, TransactionMessageHandler> map = new HashMap<>();
        for (TransactionMessageHandler handler : handlers) {
            TransactionMessageHandler prev = map.put(handler.handlerName(), handler);
            if (prev != null) {
                throw new IllegalStateException("重复的事务消息处理器: " + handler.handlerName());
            }
        }
        this.handlerMap = Map.copyOf(map);
        log.info("已注册半事务处理器: {}", this.handlerMap.keySet());
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            return resolve(msg).executeLocalTransaction(msg, arg);
        } catch (RuntimeException e) {
            log.error("半事务 execute 分发失败，ROLLBACK", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            return resolve(msg).checkLocalTransaction(msg);
        } catch (RuntimeException e) {
            // 回查不确定时不可 ROLLBACK，否则可能丢掉已落库的半消息
            log.error("半事务 check 分发失败，返回 UNKNOWN 待再查", e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    private TransactionMessageHandler resolve(Message<?> msg) {
        Object raw = msg.getHeaders().get(HANDLER_NAME_HEADER);
        String name = raw == null ? null : String.valueOf(raw);
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("事务消息缺少 " + HANDLER_NAME_HEADER);
        }
        TransactionMessageHandler handler = handlerMap.get(name);
        if (handler == null) {
            throw new IllegalStateException("未注册的事务消息处理器: " + name);
        }
        return handler;
    }
}
