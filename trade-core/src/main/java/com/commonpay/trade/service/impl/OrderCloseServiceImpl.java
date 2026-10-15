package com.commonpay.trade.service.impl;

import com.commonpay.trade.entity.Order;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.enums.PaymentStatus;
import com.commonpay.trade.enums.OrderStatus;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.mapper.OrderMapper;
import com.commonpay.trade.mapper.StockReleaseMapper;
import com.commonpay.trade.service.InventoryService;
import com.commonpay.trade.service.OrderCloseService;
import com.commonpay.trade.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCloseServiceImpl implements OrderCloseService {

    private final OrderMapper orderMapper;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final StockReleaseMapper stockReleaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeLocal(Order order, Payment payment) {
        Order current = orderMapper.selectForUpdate(order.getOrderId());
        if (current == null || current.getStatus() != OrderStatus.WAIT_PAY.getCode()
                || current.getPayDeadline() == null || current.getPayDeadline().isAfter(LocalDateTime.now())) {
            return false;
        }
        // 必须在订单行锁内重新查支付单。任务的“没有支付单”快照可能已被首付请求改变。
        Payment fresh = paymentService.getByOrderId(current.getOrderId());
        if ((payment == null) != (fresh == null)
                || (fresh != null && !fresh.getPaymentId().equals(payment.getPaymentId()))) return false;
        if (fresh != null) {
            fresh = paymentService.getForUpdate(fresh.getPaymentId());
            if (fresh.getStatus() == null || fresh.getStatus() == PaymentStatus.SUCCESS.getCode()) return false;
        }
        order = current;
        payment = fresh;
        // CAS：状态 + "是否仍然过期"一起判断。
        // deadline 条件不能省 —— 首付时 deadline 会被推后，而关单任务可能拿着推后之前的查询结果来关。
        // 也顺带挡住支付回调：它可能刚把订单改成已支付
        if (orderMapper.closeIfExpired(order.getOrderId(), LocalDateTime.now()) != 1) {
            return false;
        }
        if (payment != null && payment.getStatus() != PaymentStatus.CLOSED.getCode() && !paymentService.updateStatus(
                payment.getPaymentId(), payment.getStatus(), PaymentStatus.CLOSED.getCode())) {
            // 支付单状态在这一轮里被改过（比如回调把它改成了成功）→ 整体回滚，跳过
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "支付单状态已变化，放弃本轮关单 paymentId=" + payment.getPaymentId());
        }
        if (!inventoryService.increaseStock(order.getProductId(), order.getQuantity())) {
            log.error("定时任务中订单对应的库存不存在! orderId={}", order.getOrderId());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单对应的库存不存在,orderId=" + order.getOrderId());
        }
        // 与取消、DB 回库同事务。Redis 在提交后由任务幂等回填，提交失败不会多发一份库存。
        if (stockReleaseMapper.insert(order.getOrderId(), order.getProductId(), order.getQuantity()) != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "保存 Redis 回库记录失败");
        }
        return true;
    }
}
