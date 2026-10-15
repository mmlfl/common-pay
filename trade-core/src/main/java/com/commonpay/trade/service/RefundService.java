package com.commonpay.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.entity.Refund;

public interface RefundService extends IService<Refund> {
    /** 必须和迟到付款的本地入账处于同一事务。 */
    Refund ensureCancelledOrderRefund(Payment payment);
    Refund getByOrderId(Long orderId);
    int processDue(int batchSize);
}
