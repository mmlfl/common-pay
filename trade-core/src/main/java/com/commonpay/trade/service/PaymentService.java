package com.commonpay.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.commonpay.trade.entity.Payment;

/**
 * 支付单表基础能力（CRUD）+ 状态机更新。
 */
public interface PaymentService extends IService<Payment> {

    Payment getByOrderId(Long orderId);

    /** 仅当当前状态为 fromStatus 时改为 toStatus */
    boolean updateStatus(Long paymentId, int fromStatus, int toStatus);

    Payment getForUpdate(Long paymentId);

    /** 保存经过验证的渠道扣款事实，包括已关闭订单的迟到支付；调用方须先锁订单和支付单。 */
    boolean markSuccess(Long paymentId, String channelTradeNo, String notifyId);
}
