package com.commonpay.trade.service;
import com.commonpay.pay.api.enums.PayQueryStatus;

/**
 * 主动去渠道查一笔单的真实结果，已支付就入账。
 * <p>两个场景都靠它：
 * <ol>
 *   <li>关单时渠道说「已支付」—— 本地没有通知号，查一次把账补上</li>
 *   <li>回调迟迟不来 —— 定期查一次，别让订单永远挂着</li>
 * </ol>
 */
public interface PaymentReconcileService {

    /** @return 是否真的入账了（渠道说没支付 / 查无此单时返回 false） */
    boolean settleIfPaid(Long paymentId);
    /** 返回明确的渠道状态；PAID 已由统一入账服务处理。 */
    PayQueryStatus queryAndSettle(Long paymentId);
}
