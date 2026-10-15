package com.commonpay.trade.service;

import java.math.BigDecimal;

/**
 * 把一笔「渠道已确认支付成功」的单入账。
 * <p>回调路径和主动查单路径共用这一份逻辑 —— 两条路要落成同一个结果，
 * 分开写迟早会出现"回调能入账、查单入不了账"这种鬼故事。
 */
public interface PaymentSettlementService {

    /**
     * @param notifyId 回调带来的通知号；主动查单时没有，传 null
     * @return true = 已保存扣款事实，并更新订单或记录退款义务（含幂等重放）
     */
    boolean settle(String channelCode, Long paymentId, String channelTradeNo,
                   String notifyId, BigDecimal amount);
}
