package com.commonpay.trade.service.impl;

import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.redis.ReservationDebt;
import com.commonpay.trade.redis.RedisUtil;
import com.commonpay.trade.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 预扣义务：崩溃后「有预扣、无 Outbox」时由定时任务幂等回补 Redis。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationDebtService {

    /** 义务详情 TTL，需大于对账门槛，避免扫到 index 却读不到详情。 */
    public static final Duration DEBT_TTL = Duration.ofHours(24);

    private final RedisUtil redisUtil;
    private final OutboxService outboxService;

    public void clear(String requestId) {
        redisUtil.clearReservationDebt(requestId);
    }

    /**
     * 扫到期义务：有 Outbox 只销账；无 Outbox 则 {@link RedisUtil#returnReservationOnce} 后销账。
     *
     * @return 本轮处理条数
     */
    public int reconcileDue(Duration minAge, int limit) {
        long maxScore = System.currentTimeMillis() - Math.max(1, minAge.toMillis());
        List<String> requestIds = redisUtil.listDueReservationDebtRequestIds(maxScore, limit);
        int handled = 0;
        for (String requestId : requestIds) {
            try {
                if (reconcileOne(requestId)) {
                    handled++;
                }
            } catch (Exception e) {
                log.warn("预扣义务对账失败，下轮重试 requestId={}", requestId, e);
            }
        }
        return handled;
    }

    private boolean reconcileOne(String requestId) {
        ReservationDebt debt = redisUtil.getReservationDebt(requestId);
        if (debt == null) {
            // 详情过期或已清，清掉索引残留
            redisUtil.clearReservationDebt(requestId);
            return true;
        }
        if (!StringUtils.hasText(debt.getAttemptToken())
                || debt.getProductId() == null
                || debt.getQuantity() == null
                || debt.getQuantity() <= 0) {
            log.error("预扣义务字段残缺，仅销账 requestId={} debt={}", requestId, debt);
            redisUtil.clearReservationDebt(requestId);
            return true;
        }

        Outbox outbox = outboxService.lambdaQuery().eq(Outbox::getRequestId, requestId).one();
        if (outbox != null) {
            // 已进本地消息链路：SENT/FAILED/COMPENSATED 都不在这里回补
            redisUtil.clearReservationDebt(requestId);
            log.info("预扣义务已有 Outbox，销账 requestId={} status={}", requestId, outbox.getStatus());
            return true;
        }

        redisUtil.returnReservationOnce(debt.getProductId(), debt.getQuantity(), debt.getAttemptToken());
        redisUtil.clearReservationDebt(requestId);
        log.warn("崩溃对账：无 Outbox，已归还预扣 requestId={} productId={} quantity={}",
                requestId, debt.getProductId(), debt.getQuantity());
        return true;
    }
}
