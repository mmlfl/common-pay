package com.commonpay.trade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.enums.OutboxStatus;
import com.commonpay.trade.exception.BaseException;
import com.commonpay.trade.mq.CreateOrderTransactionHandler;
import com.commonpay.trade.mq.GlobalTransactionListener;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 事务消息受理：半消息 + 本地写 Outbox。
 * <p>仅在确认本地事务 {@code ROLLBACK} 且无 Outbox 时抛 {@link NotAcceptedException}，
 * 上层才可归还 Redis 预扣。{@code UNKNOW}/发送异常可能已落 Outbox，改抛 {@link ErrorCode#ORDER_RESULT_UNKNOWN}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAcceptanceService {

    private final OutboxService outbox;
    private final RocketMQTemplate rocketMQTemplate;

    /** 发送事务消息；仅当本地事务 COMMIT 才算受理成功。 */
    public void accept(CreateOrderMessage message) {
        Message<CreateOrderMessage> msg = MessageBuilder.withPayload(message)
                .setHeader(GlobalTransactionListener.HANDLER_NAME_HEADER, CreateOrderTransactionHandler.HANDLER_NAME)
                .setHeader("requestId", message.getRequestId())
                .build();
        TransactionSendResult txResult;
        try {
            txResult = rocketMQTemplate.sendMessageInTransaction(
                    CreateOrderMessage.TOPIC,
                    msg,
                    message
            );
        } catch (RuntimeException e) {
            // 发送异常时本地事务可能已 COMMIT 并写入 Outbox，禁止上层归还预扣
            if (existsByRequestId(message.getRequestId())) {
                log.warn("事务消息发送异常但 Outbox 已存在，保留预扣 requestId={}",
                        message.getRequestId(), e);
                throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN, e);
            }
            throw new NotAcceptedException(e);
        }
        LocalTransactionState state = txResult == null ? null : txResult.getLocalTransactionState();
        if (state == LocalTransactionState.COMMIT_MESSAGE) {
            return;
        }
        if (state == LocalTransactionState.ROLLBACK_MESSAGE) {
            log.warn("事务消息本地回滚 requestId={}", message.getRequestId());
            // 双检：异常路径下偶发 Outbox 已存在时仍不可归还
            if (existsByRequestId(message.getRequestId())) {
                throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN,
                        new IllegalStateException("本地标记 ROLLBACK 但 Outbox 已存在"));
            }
            throw new NotAcceptedException(
                    new IllegalStateException("本地事务 ROLLBACK"));
        }
        // UNKNOW / null：可能已写 Outbox，Broker 仍可能回查 COMMIT
        log.warn("事务消息结果未知 requestId={} state={}", message.getRequestId(), state);
        throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN,
                new IllegalStateException("本地事务未确认: " + state));
    }

    /**
     * 供 Handler.execute 调用：写 Outbox（SENT = 已随事务消息绑定）。
     *
     * @return true 新插入；false 已存在（幂等，应 COMMIT）
     */
    public boolean persistAccepted(CreateOrderMessage body) {
        try {
            if (!outbox.save(new Outbox()
                    .setRequestId(body.getRequestId())
                    .setTopic(CreateOrderMessage.TOPIC)
                    .setPayload(JSON.toJSONString(body))
                    .setRetryCount(0)
                    .setStatus(OutboxStatus.SENT.getCode()))) {
                throw new IllegalStateException("Outbox 插入未成功");
            }
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof DuplicateKeyException) {
                return false;
            }
            throw e;
        }
    }

    public boolean existsByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        return outbox.lambdaQuery().eq(Outbox::getRequestId, requestId).exists();
    }

    public static final class NotAcceptedException extends BaseException {
        public NotAcceptedException(Throwable cause) {
            super(ErrorCode.ORDER_ACCEPT_FAILED, cause);
        }
    }
}
