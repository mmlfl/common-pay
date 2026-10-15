package com.commonpay.trade.mq;

import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

/**
 * 半事务本地逻辑。由 {@link GlobalTransactionListener} 按 {@link #handlerName()} 分发。
 */
public interface TransactionMessageHandler {

    /** 写入消息 HEADER，须全局唯一。 */
    String handlerName();

    RocketMQLocalTransactionState executeLocalTransaction(Message<?> msg, Object arg);

    RocketMQLocalTransactionState checkLocalTransaction(Message<?> msg);
}
