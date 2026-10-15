package com.commonpay.trade.service;

import com.commonpay.trade.entity.Order;
import com.commonpay.trade.entity.Payment;

/**
 * 关单的本地落库部分。单条一个事务 —— 从 {@link OrderService#closeExpiredOrders} 里拆出来，
 * 一是为了让批次里一条失败不拖垮其它条，二是为了让远程调用留在事务外。
 */
public interface OrderCloseService {

    /**
     * 关闭本地状态并归还库存。
     *
     * @param payment 可为 null（该单从没发起过支付）；有值时必须带上当前状态，用于 CAS
     * @return 是否真的关掉了。false = 状态已被别人改过（回调、或另一轮关单），本轮跳过
     */
    boolean closeLocal(Order order, Payment payment);
}
